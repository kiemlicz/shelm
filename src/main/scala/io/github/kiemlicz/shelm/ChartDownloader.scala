package io.github.kiemlicz.shelm

import io.github.kiemlicz.shelm.HelmPlugin.pullChart
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveInputStream, ArchiveStreamFactory}
import org.apache.commons.compress.compressors.{CompressorInputStream, CompressorStreamFactory}
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.input.CloseShieldInputStream
import sbt.IO
import sbt.io.syntax.fileToRichFile
import sbt.util.Logger

import java.io.{BufferedInputStream, File, InputStream}
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.util.Try

object ChartDownloader {
  case class CacheKey(relPath: String) extends AnyVal

  private val chartsCacheDirectories = new ConcurrentHashMap[CacheKey, File]()

  private object CacheKey {
    private def sanitizeRepositoryName(repo: String): String = repo
      .replace('/', '_')
      .replace('\\', '_')
      .replace(':', '_')

    def apply(repositoryName: String, chartName: ChartName, chartVersion: String): CacheKey = CacheKey(
      s"${sanitizeRepositoryName(repositoryName)}/${chartName.name}/${chartName.name}-$chartVersion"
    )

    def apply(chartName: ChartName, chartVersion: String, repoUri: URI): CacheKey = CacheKey(
      s"${sanitizeRepositoryName(repoUri.toString)}/${chartName.name}/${chartName.name}-$chartVersion"
    )

    def apply(uri: URI): CacheKey = CacheKey(sanitizeRepositoryName(uri.toString))
  }

  def download(chartLocation: ChartLocation, downloadDir: File, cacheDir: File, sbtLogger: Logger): File = {
    val cachedChartKey = chartLocation match {
      case ChartLocation.Remote(_, uri) => Some(CacheKey(FilenameUtils.getName(uri.getPath)))
      case ChartLocation.AddedRepository(name, ChartRepositoryName(repoName), Some(chartVersion)) => Some(
        CacheKey(repoName, name, chartVersion)
      )
      case ChartLocation.RemoteRepository(name, uri, _, Some(chartVersion)) => Some(CacheKey(name, chartVersion, uri))
      case ChartLocation.RemoteOciRegistry(name, uri, Some(chartVersion)) => Some(CacheKey(name, chartVersion, uri))
      case _ => Option.empty
    }
    cachedChartKey match {
      case Some(key) =>
        val chartInCacheLocation = chartsCacheDirectories.computeIfAbsent(
          key, k => {
            cacheDir / k.relPath match {
              case f if f.isDirectory =>
                sbtLogger.info(s"Cache hit for: ${chartLocation.chartName}")
                f / chartLocation.chartName.name
              case f =>
                sbtLogger.info(s"Cache miss for: ${chartLocation.chartName}")
                download(chartLocation, f, sbtLogger)
            }
          }
        )
        IO.copyDirectory(chartInCacheLocation, downloadDir / chartLocation.chartName.name)
        downloadDir / chartLocation.chartName.name
      case None => download(chartLocation, downloadDir, sbtLogger)
    }
  }

  /**
    *
    * @param chartLocation Chart reference
    * @return directory containing Chart
    */
  private def download(chartLocation: ChartLocation, downloadDir: File, sbtLogger: Logger): File = {
    chartLocation match {
      case ChartLocation.Local(_, f) =>
        val dst = downloadDir / f.getName
        IO.copyDirectory(f, dst, overwrite = true)
        dst
      case ChartLocation.Remote(_, uri) =>
        val topDirs = extractArchive(uri, downloadDir)
        if (topDirs.size != 1)
          throw new IllegalStateException(
            s"Helm Chart: $uri is improperly packaged, contains: $topDirs top-level entries whereas only one is allowed"
          )
        else
          downloadDir / topDirs.head
      case ChartLocation.AddedRepository(ChartName(name), ChartRepositoryName(repoName), chartVersion) =>
        val options = s"$repoName/$name -d $downloadDir${chartVersion.map(v => s" --version $v").getOrElse("")} --untar"
        IO.delete(downloadDir)
        pullChart(options, sbtLogger)
        downloadDir / name
      case ChartLocation.RemoteRepository(ChartName(name), uri, auth, chartVersion) =>
        val authOpts = HelmPlugin.chartRepositoryCommandFlags(auth)
        val allOptions = s"--repo $uri $name $authOpts -d $downloadDir${
          chartVersion.map(v => s" --version $v").getOrElse("")
        } --untar"
        IO.delete(downloadDir)
        pullChart(allOptions, sbtLogger)
        downloadDir / name
      case ChartLocation.RemoteOciRegistry(ChartName(name), uri, chartVersion) =>
        val allOptions = s"$uri -d $downloadDir${chartVersion.map(v => s" --version $v").getOrElse("")} --untar"
        IO.delete(downloadDir)
        pullChart(allOptions, sbtLogger)
        downloadDir / name
    }
  }

  def extractArchive(archiveUri: URI, unpackTo: File): Set[String] = {
    val topDirs = mutable.Set.empty[String]
    open(archiveUri.toURL.openStream())
      .getOrElse(throw new IllegalStateException(s"Unable to extract Helm Chart from: $archiveUri"))
      .foreach {
        case (entry, is) =>
          try {
            val archiveEntry = unpackTo / entry.getName
            IO.write(archiveEntry, IO.readBytes(is))
            for {
              relativeFile <- IO.relativizeFile(unpackTo, archiveEntry)
              topDir <- relativeFile.getPath.split(File.separator).headOption
            } yield topDirs.add(topDir)
          } finally {
            is.close()
          }
      }
    topDirs.toSet
  }

  def open(inputStream: InputStream): Try[Iterator[(ArchiveEntry, InputStream)]] = for {
    uncompressedInputStream <- createUncompressedStream(inputStream)
    archiveInputStream <- createArchiveStream(uncompressedInputStream)
  } yield createIterator(archiveInputStream)

  private def createUncompressedStream(inputStream: InputStream): Try[CompressorInputStream] = Try {
    new CompressorStreamFactory().createCompressorInputStream(getMarkableStream(inputStream))
  }

  private def createArchiveStream(uncompressedInputStream: CompressorInputStream): Try[ArchiveInputStream] = Try {
    new ArchiveStreamFactory().createArchiveInputStream(getMarkableStream(uncompressedInputStream))
  }

  private def createIterator(archiveInputStream: ArchiveInputStream): Iterator[(ArchiveEntry, InputStream)] =
    new Iterator[(ArchiveEntry, InputStream)] {
      var latestEntry: ArchiveEntry = _

      override def hasNext: Boolean = {
        latestEntry = archiveInputStream.getNextEntry
        latestEntry != null
      }

      override def next(): (ArchiveEntry, InputStream) = (latestEntry, new CloseShieldInputStream(archiveInputStream))
    }

  private def getMarkableStream(inputStream: InputStream): InputStream =
    if (inputStream.markSupported()) inputStream
    else new BufferedInputStream(inputStream)
}
