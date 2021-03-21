package com.shelm
package exception

class HelmCommandException(message: String, exitCode: Int) extends RuntimeException(s"(exit code: $exitCode) $message")
