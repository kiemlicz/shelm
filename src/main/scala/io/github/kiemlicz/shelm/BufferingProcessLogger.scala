package io.github.kiemlicz.shelm

import io.github.kiemlicz.shelm.exception.HelmCommandException

import scala.sys.process.ProcessLogger

class BufferingProcessLogger extends ProcessLogger {
  val stdOutBuf: StringBuffer = new StringBuffer()
  val buf: StringBuffer = new StringBuffer()

  def out(s: => String): Unit = {
    stdOutBuf.append(s + "\n")
    buf.append(s + "\n")
  }

  def err(s: => String): Unit = buf.append(s + "\n")

  def buffer[T](f: => T): T = f
}

/**
  * @param stdOut       - standard output
  * @param entireOutput - standard output and standard error, as would be shown to terminal user
  */
case class ProcessOutput(stdOut: String, entireOutput: String) {
  override def toString: String = entireOutput
}

object ProcessOutput {
  def apply(logger: BufferingProcessLogger): ProcessOutput =
    ProcessOutput(logger.stdOutBuf.toString.trim, logger.buf.toString.trim)
}

sealed abstract class HelmProcessResult(exitCode: Int, output: ProcessOutput)

object HelmProcessResult {
  def apply(exitCode: Int, log: BufferingProcessLogger): HelmProcessResult =
    if (exitCode == 0) Success(ProcessOutput(log)) else Failure(exitCode, ProcessOutput(log))

  case class Success(output: ProcessOutput) extends HelmProcessResult(0, output)

  case class Failure(exitCode: Int, output: ProcessOutput) extends HelmProcessResult(exitCode, output)

  def getOrThrow(processResult: HelmProcessResult): ProcessOutput = processResult match {
    case HelmProcessResult.Failure(exitCode, output) => throw new HelmCommandException(output, exitCode)
    case HelmProcessResult.Success(output) => output
  }
}
