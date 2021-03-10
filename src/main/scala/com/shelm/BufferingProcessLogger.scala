package com.shelm

import com.shelm.exception.HelmCommandException

import scala.sys.process.ProcessLogger

case class HelmProcessResult(exitCode: Int, output: BufferingProcessLogger)

class BufferingProcessLogger extends ProcessLogger {
  val buf: StringBuffer = new StringBuffer()
  def out(s: => String): Unit = buf.append(s + "\n")
  def err(s: => String): Unit = buf.append(s + "\n")
  def buffer[T](f: => T): T = f
}

object HelmProcessResult {
  def isSuccess(processOutput: HelmProcessResult): Boolean = processOutput.exitCode == 0
  def isFailure(processOutput: HelmProcessResult): Boolean = !isSuccess(processOutput)
  def throwOnFailure(processOutput: HelmProcessResult): Unit = if(isFailure(processOutput)) throw new HelmCommandException(processOutput.output.buf.toString, processOutput.exitCode)
}
