package io.github.kiemlicz.shelm

import io.github.kiemlicz.shelm.exception.ImproperVersionException
import org.scalatest.flatspec.AnyFlatSpec

class SemVer2Test extends AnyFlatSpec {
  "major.minor.patch" must "be valid SemVer2" in {
    val v = SemVer2("0.1.0")
    assert(v.major == 0)
    assert(v.minor == 1)
    assert(v.patch == 0)
    assert(v.preRelease.isEmpty)
    assert(v.build.isEmpty)
  }

  "major.minor.patch with pre release" must "be valid SemVer2" in {
    val v1 = SemVer2("0.1.0-some.pre.data")
    assert(v1.major == 0)
    assert(v1.minor == 1)
    assert(v1.patch == 0)
    assert(v1.preRelease.get == "some.pre.data")
    assert(v1.build.isEmpty)

    val v2 = SemVer2("1.2.3-some.pre.data-even-more-pre.123")
    assert(v2.major == 1)
    assert(v2.minor == 2)
    assert(v2.patch == 3)
    assert(v2.preRelease.get == "some.pre.data-even-more-pre.123")
    assert(v2.build.isEmpty)
  }

  "major.minor.patch with pre release and build meta" must "be valid SemVer2" in {
    val v1 = SemVer2("1.2.4-some.pre.data+build.meta.data")
    assert(v1.major == 1)
    assert(v1.minor == 2)
    assert(v1.patch == 4)
    assert(v1.preRelease.get == "some.pre.data")
    assert(v1.build.get == "build.meta.data")

    val v2 = SemVer2("0.1.0-some.pre.data-123+build.meta.data")
    assert(v2.major == 0)
    assert(v2.minor == 1)
    assert(v2.patch == 0)
    assert(v2.preRelease.get == "some.pre.data-123")
    assert(v2.build.get == "build.meta.data")
  }

  "missing patch segment" can "not be a valid SemVer2" in {
    assertThrows[ImproperVersionException] {
      SemVer2("0.1")
    }
  }

  "major.minor.patch with non-digit" must "not be valid SemVer2" in {
    assertThrows[ImproperVersionException] {
      SemVer2("0.1.A")
    }
  }

  "major.minor.patch with pre release and broken build meta" must "not be valid SemVer2" in {
    assertThrows[ImproperVersionException] {
      SemVer2("0.1.0-some.pre.data-123+build.meta.data+123+something.1")
    }
  }

  "SemVer2 toString" must "pretty print" in {
    val ver1 = SemVer2(1, 2, 3, Some("pre.release-something-1"))
    assert(ver1.toString == "1.2.3-pre.release-something-1")
    val ver2 = SemVer2(0, 2, 3, Some("pre"), Some("buildMeta.1"))
    assert(ver2.toString == "0.2.3-pre+buildMeta.1")
    val ver3 = SemVer2(0, 1, 0)
    assert(ver3.toString == "0.1.0")
    val ver4 = SemVer2(0, 1, 0, build = Some("meta-1.3"))
    assert(ver4.toString == "0.1.0+meta-1.3")
  }

  "SemVer2" can "not be constructed" in {
    assertThrows[IllegalArgumentException](
      SemVer2(0, 1, 0, "bla+meta")
    )
    assertThrows[IllegalArgumentException](
      SemVer2(0, 1, 0, "bla", "build-ok+extraplus")
    )
  }

  "SemVer2" must "be constructed when leading v is used" in {
    val v1 = SemVer2("v1.2.4-some.pre.data+build.meta.data")
    assert(v1.major == 1)
    assert(v1.minor == 2)
    assert(v1.patch == 4)
    assert(v1.preRelease.get == "some.pre.data")
    assert(v1.build.get == "build.meta.data")

    val v2 = SemVer2("v0.1.0-some.pre.data-123+build.meta.data")
    assert(v2.major == 0)
    assert(v2.minor == 1)
    assert(v2.patch == 0)
    assert(v2.preRelease.get == "some.pre.data-123")
    assert(v2.build.get == "build.meta.data")

    assertThrows[ImproperVersionException] {
      SemVer2("b0.1.0")
    }
  }
}
