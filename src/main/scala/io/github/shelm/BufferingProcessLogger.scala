package io.github.shelm

import io.github.shelm.exception.HelmCommandException

import scala.sys.process.ProcessLogger

class BufferingProcessLogger extends ProcessLogger {
  val buf: StringBuffer = new StringBuffer()

  def out(s: => String): Unit = buf.append(s + "\n")

  def err(s: => String): Unit = buf.append(s + "\n")

  def buffer[T](f: => T): T = f
}

sealed abstract class HelmProcessResult(exitCode: Int, output: String)

object HelmProcessResult {
  def apply(exitCode: Int, log: BufferingProcessLogger): HelmProcessResult =
    if (exitCode == 0) Success(log.buf.toString.trim) else Failure(exitCode, log.buf.toString.trim)

  case class Success(output: String) extends HelmProcessResult(0, output)

  case class Failure(exitCode: Int, output: String) extends HelmProcessResult(exitCode, output)

  def throwOnFailure(processOutput: HelmProcessResult): Unit = processOutput match {
    case HelmProcessResult.Failure(exitCode, output) => throw new HelmCommandException(output, exitCode)
    case _ =>
  }
}
