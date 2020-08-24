package com.shelm

import java.io.{BufferedInputStream, File, InputStream}

import com.shelm.HelmPlugin.retrying
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveInputStream, ArchiveStreamFactory}
import org.apache.commons.compress.compressors.{CompressorInputStream, CompressorStreamFactory}
import org.apache.commons.io.input.CloseShieldInputStream
import sbt.IO
import sbt.util.Logger

import scala.collection.mutable
import scala.util.Try

object ChartDownloader {
  /**
    *
   * @param chartLocation
    * @param downloadDir
    * @return file containing Chart's root, e.g. `downloadDir / top-level_file`
    */
  def download(chartLocation: ChartLocation, downloadDir: File, sbtLogger: Logger): File = {
    import sbt.io.syntax.fileToRichFile
    chartLocation match {
      case ChartLocation.Local(f) =>
        IO.copyDirectory(f, downloadDir / f.getName, overwrite = true)
        downloadDir / f.getName
      case ChartLocation.Remote(uri) =>
        val topDirs = mutable.Set.empty[String]
        open(uri.toURL.openStream())
          .getOrElse(throw new IllegalStateException(s"Unable to download Helm Chart from: $uri"))
          .foreach {
            case (entry, is) =>
              try {
                val archiveEntry = downloadDir / entry.getName
                for {
                  relativeFile <- IO.relativizeFile(downloadDir, archiveEntry)
                  topDir <- relativeFile.getPath.split(File.separator).headOption
                } yield topDirs.add(topDir)
                IO.write(archiveEntry, IO.readBytes(is))
              } finally {
                is.close()
              }
          }
        if (topDirs.size != 1)
          throw new IllegalStateException(s"Helm Chart: $uri is improperly packaged, contains: $topDirs top-level entries whereas only one is allowed")
        else
          downloadDir / topDirs.head
      case ChartLocation.Repository(repo, name, chartVersion) =>
        val cmd = s"helm pull $repo/$name -d $downloadDir${chartVersion.map(v => s" --version $v").getOrElse("")} --untar"
        val d = downloadDir / name // the name matches top-level archive dir after untar
        IO.delete(d) // ensure dir is empty
        retrying(cmd, sbtLogger)
        d
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
