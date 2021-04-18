package com.shelm

import com.shelm.HelmPlugin.pullChart
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveInputStream, ArchiveStreamFactory}
import org.apache.commons.compress.compressors.{CompressorInputStream, CompressorStreamFactory}
import org.apache.commons.io.input.CloseShieldInputStream
import sbt.IO
import sbt.util.Logger

import java.io.{BufferedInputStream, File, InputStream}
import scala.collection.mutable
import scala.util.Try

object ChartDownloader {
  /**
   *
   * @param chartLocation Chart reference
   * @return directory containing Chart
   */
  def download(chartLocation: ChartLocation, downloadDir: File, sbtLogger: Logger): File = {
    import sbt.io.syntax.fileToRichFile
    chartLocation match {
      case ChartLocation.Local(_, f) =>
        val dst = downloadDir / f.getName
        IO.copyDirectory(f, dst, overwrite = true)
        dst
      case ChartLocation.Remote(_, uri) =>
        val topDirs = mutable.Set.empty[String]
        open(uri.toURL.openStream())
          .getOrElse(throw new IllegalStateException(s"Unable to download Helm Chart from: $uri"))
          .foreach {
            case (entry, is) =>
              try {
                val archiveEntry = downloadDir / entry.getName
                IO.write(archiveEntry, IO.readBytes(is))
                for {
                  relativeFile <- IO.relativizeFile(downloadDir, archiveEntry)
                  topDir <- relativeFile.getPath.split(File.separator).headOption
                } yield topDirs.add(topDir)
              } finally {
                is.close()
              }
          }
        if (topDirs.size != 1)
          throw new IllegalStateException(
            s"Helm Chart: $uri is improperly packaged, contains: $topDirs top-level entries whereas only one is allowed"
          )
        else
          downloadDir / topDirs.head
      case ChartLocation.AddedRepository(chartName, ChartRepositoryName(repoName), chartVersion) =>
        val options = s"$repoName/$chartName -d $downloadDir${chartVersion.map(v => s" --version $v").getOrElse("")} --untar"
        IO.delete(downloadDir)
        pullChart(options, sbtLogger)
        downloadDir / chartName
      case ChartLocation.RemoteRepository(chartName, uri, settings, chartVersion) =>
        val authOpts = HelmPlugin.chartRepositoryCommandFlags(settings)
        val allOptions = s"--repo $uri $chartName $authOpts -d $downloadDir${chartVersion.map(v => s" --version $v").getOrElse("")} --untar"
        IO.delete(downloadDir)
        pullChart(allOptions, sbtLogger)
        downloadDir / chartName
    }
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
