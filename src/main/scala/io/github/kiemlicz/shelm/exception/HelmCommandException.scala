package io.github.kiemlicz.shelm
package exception

import sbt.librarymanagement.VersionNumber

class HelmCommandException(output: ProcessOutput, exitCode: Int)
  extends RuntimeException(s"Exit code: $exitCode\nStdOut: ${output.stdOut}\nStdErr: ${output.stdErr}")

class ImproperVersionException(ver: VersionNumber)
  extends RuntimeException(s"Version: '$ver' doesn't comply with SemVer2")
