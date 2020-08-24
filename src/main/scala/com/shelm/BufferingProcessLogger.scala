package com.shelm

import scala.sys.process.ProcessLogger

class BufferingProcessLogger extends ProcessLogger {
  val buf: StringBuffer = new StringBuffer()

  override def out(s: => String): Unit = buf.append(s+"\n")

  override def err(s: => String): Unit = buf.append(s+"\n")

  override def buffer[T](f: => T): T = f

}
