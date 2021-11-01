package io.github.kiemlicz.shelm
package exception

class HelmCommandException(output: ProcessOutput, exitCode: Int)
  extends RuntimeException(s"(exit code: $exitCode) ${output.entireOutput}")

class ImproperVersionException(ver: String)
  extends RuntimeException(s"Version: '$ver' doesn't comply with SemVer2")
