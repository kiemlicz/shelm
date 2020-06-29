package com.shelm

import sbt.{Def, _}
import Keys._

object HelmPlugin extends AutoPlugin {
  object autoImport {
    val helm = config("helm")

    val helmChart = taskKey[File]("Chart directory")

    val baseHelmSettings: Seq[Setting[_]] = Seq(

    )
  }
  import autoImport._

  override val projectSettings: Seq[Setting[_]] = ??? //inConfig(helm)
}
