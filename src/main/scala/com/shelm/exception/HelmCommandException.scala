package com.shelm
package exception

class HelmCommandException(message: String, exitCode: Int) extends RuntimeException(message)
