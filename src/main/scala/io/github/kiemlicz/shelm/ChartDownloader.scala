package io.github.kiemlicz.shelm

import io.github.kiemlicz.shelm.HelmPlugin.pullChart
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveInputStream, ArchiveStreamFactory}
import org.apache.commons.compress.compressors.{CompressorInputStream, CompressorStreamFactory}
import org.apache.commons.io.input.CloseShieldInputStream
import sbt.IO
import sbt.io.syntax.fileToRichFile
import sbt.util.Logger

import java.io.{BufferedInputStream, File, InputStream}
import java.net.URI
import scala.collection.mutable
import scala.util.Try

object ChartDownloader {
  /**
    *
    * @param chartLocation Chart reference
    * @return directory containing Chart
    */
  def download(chartLocation: ChartLocation, downloadDir: File, cacheDir: Option[File], sbtLogger: Logger): File = {
    def pullFromCacheOrRemote(
      chartName: ChartName, chartVersion: Option[String], downloadFunction: () => File
    ): File = {
      if (cacheDir.nonEmpty && chartVersion.nonEmpty) {
        pullFromCache(chartName, chartVersion.get, cacheDir.get, downloadDir).map { chartDir => {
          sbtLogger.info(s"Helm chart cache hit for ${chartName.name}")
          chartDir
        }
        }.getOrElse {
          sbtLogger.info(s"Helm chart cache miss for ${chartName.name} - downloading from remote")
          val downloadedChartDirectory = downloadFunction()
          sbtLogger.info(s"saving  ${chartName.name} to cache in ${cacheDir.get.getName}")
          saveToCache(downloadedChartDirectory, chartName, chartVersion.get, cacheDir.get)
          downloadedChartDirectory
        }
      } else {
        downloadFunction()
      }
    }
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
      case ChartLocation.AddedRepository(chartName, ChartRepositoryName(repoName), chartVersion) =>
        pullFromCacheOrRemote(
          chartName, chartVersion, () => {
            val options = s"$repoName/${chartName.name} -d $downloadDir${
              chartVersion.map(v => s" --version $v").getOrElse("")
            } --untar"
            IO.delete(downloadDir)
            pullChart(options, sbtLogger)
            downloadDir / chartName.name
          }
        )
      case ChartLocation.RemoteRepository(chartName, uri, settings, chartVersion) =>
        pullFromCacheOrRemote(
          chartName, chartVersion, () => {
            val authOpts = HelmPlugin.chartRepositoryCommandFlags(settings)
            val allOptions = s"--repo $uri ${chartName.name} $authOpts -d $downloadDir${
              chartVersion.map(v => s" --version $v").getOrElse("")
            } --untar"
            IO.delete(downloadDir)
            pullChart(allOptions, sbtLogger)
            downloadDir / chartName.name
          }
        )
    }
  }

  def calculateCacheChartDir(
    chartName: ChartName, chartVersion: String, cacheBaseDir: File
  ): File = cacheBaseDir / s"${chartName.name}" / s"${chartName.name}-$chartVersion"

  def pullFromCache(
    chartName: ChartName,
    chartVersion: String,
    cacheBaseDir: File,
    downloadDir: File,
  ): Option[File] = {
    val chartDir = calculateCacheChartDir(chartName, chartVersion, cacheBaseDir)
    if (chartDir.isDirectory) {
      IO.copyDirectory(chartDir, downloadDir / chartName.name, overwrite = true)
      Option(downloadDir / chartName.name)
    } else {
      Option.empty
    }
  }

  def saveToCache(chartDir: File, chartName: ChartName, chartVersion: String, cacheBaseDir: File): Unit = {
    IO.copyDirectory(chartDir, calculateCacheChartDir(chartName, chartVersion, cacheBaseDir), overwrite = true)
  }

  def cleanCache(cacheDir: File, noVersionToKeep: Int, logger: Logger): Int = {
    val numOfCleanedDirs = cacheDir.listFiles()
      .flatMap(f => f.listFiles().sortBy(g => g.attributes.lastModifiedTime()).dropRight(noVersionToKeep)).map(f => {
      logger.debug(s"removing helm cache directory: $f")
      IO.delete(f)
    }
    ).length
    cacheDir.listFiles().filter(f => f.listFiles().length == 0).foreach(_.delete())
    numOfCleanedDirs
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
