package io.github.kiemlicz.shelm

import io.github.kiemlicz.shelm.SemVer2.{BuildRegex, PreReleaseRegex}
import io.github.kiemlicz.shelm.exception.ImproperVersionException

final case class SemVer2(
  major: Long,
  minor: Long,
  patch: Long,
  preRelease: Option[String] = None,
  build: Option[String] = None
) {
  require(
    preRelease.forall(p => PreReleaseRegex.pattern.matcher(p).matches()),
    s"pre-release part: $preRelease doesn't match SemVer2 spec"
  )
  require(
    build.forall(b => BuildRegex.pattern.matcher(b).matches()),
    s"build part: $build doesn't match SemVer2 spec"
  )

  override def toString: String = {
    val sb = new StringBuilder(s"$major.$minor.$patch")
    preRelease.foreach { p =>
      sb.append("-").append(p)
    }
    build.foreach { b =>
      sb.append("+").append(b)
    }
    sb.toString()
  }
}

object SemVer2 {
  val PreReleaseRegex =
    """((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*)""".r
  val BuildRegex = """([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*)""".r
  val VersionRegex =
    """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$"""
      .r

  def apply(major: Long, minor: Long, patch: Long, preRelease: String): SemVer2 = SemVer2(
    major, minor, patch, Some(preRelease), None
  )

  def apply(major: Long, minor: Long, patch: Long, preRelease: String, build: String): SemVer2 = SemVer2(
    major, minor, patch, Some(preRelease), Some(build)
  )

  def apply(s: String): SemVer2 = s match {
    case VersionRegex(major, minor, patch, null, null) =>
      SemVer2(major.toLong, minor.toLong, patch.toLong, None, None)
    case VersionRegex(major, minor, patch, preRelease, null) =>
      SemVer2(major.toLong, minor.toLong, patch.toLong, Some(preRelease), None)
    case VersionRegex(major, minor, patch, null, build) =>
      SemVer2(major.toLong, minor.toLong, patch.toLong, None, Some(build))
    case VersionRegex(major, minor, patch, preRelease, build) =>
      SemVer2(major.toLong, minor.toLong, patch.toLong, Some(preRelease), Some(build))
    case _ => throw new ImproperVersionException(s"$s cannot be parsed as SemVer2")
  }
}