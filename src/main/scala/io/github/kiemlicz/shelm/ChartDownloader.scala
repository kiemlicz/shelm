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
import java.security.MessageDigest
import java.util.concurrent.locks.{Lock, ReentrantLock}
import scala.collection.{concurrent, mutable}
import scala.util.Try

object ChartDownloader {
  case class CacheKey(relPath: String) extends AnyVal

  private val locks = concurrent.TrieMap[CacheKey, Lock]()

  object CacheKey {
    def MD5(str: String): String = MessageDigest
      .getInstance("MD5")
      .digest(str.getBytes("UTF-8"))
      .map("%02x".format(_))
      .mkString("").substring(0, 10)

    def apply(repositoryName: String, chartName: ChartName, chartVersion: String): CacheKey = CacheKey(
      s"${MD5(repositoryName)}/${chartName.name}/${chartName.name}-$chartVersion"
    )

    def apply(chartName: ChartName, chartVersion: String, repoUri: URI): CacheKey = CacheKey(
      s"${MD5(repoUri.toString)}/${chartName.name}/${chartName.name}-$chartVersion"
    )

    def apply(uri: URI): CacheKey = CacheKey(MD5(uri.toString))
  }

  def download(chartLocation: ChartLocation, downloadDir: File, cacheDir: File, sbtLogger: Logger): File = {
    val cachedChartKey = chartLocation match {
      case _: ChartLocation.Local => Option.empty
      case ChartLocation.Remote(_, uri) => Some(CacheKey(FilenameUtils.getName(uri.getPath)))
      case ChartLocation.AddedRepository(name, ChartRepositoryName(repoName), Some(chartVersion)) => Some(
        CacheKey(repoName, name, chartVersion)
      )
      case ChartLocation.AddedRepository(name, ChartRepositoryName(repoName), None) => Option.empty
      case ChartLocation.RemoteRepository(name, uri, _, Some(chartVersion)) => Some(CacheKey(name, chartVersion, uri))
      case ChartLocation.RemoteRepository(name, uri, _, None) => Option.empty
    }
    cachedChartKey.map(key => {
      val lock: Lock = locks.getOrElseUpdate(key, new ReentrantLock())
      lock.lock()
      try {
        val chartCacheDirectory = cacheDir / key.relPath
        chartCacheDirectory match {
          case f if f.isDirectory =>
            sbtLogger.info(s"Cache hit for: ${chartLocation.chartName}")
          case f =>
            sbtLogger.info(s"Cache miss for: ${chartLocation.chartName}")
            download(chartLocation, f, sbtLogger)
        }
        IO.copyDirectory(chartCacheDirectory, downloadDir, overwrite = true)
      } finally {
        lock.unlock()
      }
      downloadDir / chartLocation.chartName.name
    }
    ).getOrElse(download(chartLocation, downloadDir, sbtLogger))
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
      case ChartLocation.RemoteRepository(ChartName(name), uri, settings, chartVersion) =>
        val authOpts = HelmPlugin.chartRepositoryCommandFlags(settings)
        val allOptions = s"--repo $uri $name $authOpts -d $downloadDir${
          chartVersion.map(v => s" --version $v").getOrElse("")
        } --untar"
        IO.delete(downloadDir)
        pullChart(allOptions, sbtLogger)
        downloadDir / name
    }
  }

  def cleanCache(cacheBaseDir: File, noVersionToKeep: Int, logger: Logger): Int = {
//    val numOfCleanedDirs = cacheBaseDir.listFiles()
//      .flatMap(f => f.listFiles())
//      .flatMap(f => f.listFiles().sortBy(g => g.attributes.lastModifiedTime()).dropRight(noVersionToKeep)).map(f => {
//      logger.info(s"Removing helm cache directory: $f")
//      IO.delete(f)
//    }
//    ).length
//    cacheBaseDir.listFiles().filter(f => f.listFiles().length == 0).foreach(_.delete())
//    numOfCleanedDirs
    0
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
