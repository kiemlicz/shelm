package io.github.kiemlicz.shelm

import io.github.kiemlicz.shelm.exception.HelmCommandException

import scala.sys.process.ProcessLogger

class BufferingProcessLogger extends ProcessLogger {
  val stdOutBuf: StringBuffer = new StringBuffer()
  val stdErrBuf: StringBuffer = new StringBuffer()

  def out(s: => String): Unit = stdOutBuf.append(s + "\n")

  def err(s: => String): Unit = stdErrBuf.append(s + "\n")

  def buffer[T](f: => T): T = f
}

case class ProcessOutput(stdOut: String, stdErr: String)

object ProcessOutput {
  def apply(logger: BufferingProcessLogger): ProcessOutput =
    ProcessOutput(logger.stdOutBuf.toString.trim, logger.stdErrBuf.toString.trim)
}

sealed abstract class HelmProcessResult(exitCode: Int, output: ProcessOutput)

object HelmProcessResult {
  def apply(exitCode: Int, log: BufferingProcessLogger): HelmProcessResult =
    if (exitCode == 0) Success(ProcessOutput(log)) else Failure(exitCode, ProcessOutput(log))

  case class Success(output: ProcessOutput) extends HelmProcessResult(0, output)

  case class Failure(exitCode: Int, output: ProcessOutput) extends HelmProcessResult(exitCode, output)

  def throwOnFailure(processOutput: HelmProcessResult): Unit = processOutput match {
    case HelmProcessResult.Failure(exitCode, output) => throw new HelmCommandException(output, exitCode)
    case _ =>
  }
}
