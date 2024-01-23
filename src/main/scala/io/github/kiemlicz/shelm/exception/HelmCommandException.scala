package io.github.kiemlicz.shelm
package exception

class HelmCommandException(output: ProcessOutput, exitCode: Int)
  extends RuntimeException(s"(exit code: $exitCode) ${output.entireOutput}")

class ImproperVersionException(ver: String)
  extends RuntimeException(s"Version: '$ver' doesn't comply with SemVer2")

class HelmPublishException(registry: ChartRepo, resultCode: Int, responseBody: Option[String])
  extends RuntimeException(s"Publish to ${registry.uri()} failed with HTTP code: ${resultCode}${responseBody.map(r => s", response: $r").getOrElse("")}")

class HelmPublishTaskException(errors: Seq[Throwable])
  extends RuntimeException(s"Publish failed with following #${errors.length} errors:\n${errors.map(_.getMessage).mkString("\n")}")
