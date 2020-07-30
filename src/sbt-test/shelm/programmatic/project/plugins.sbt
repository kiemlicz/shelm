libraryDependencies ++= Seq(
  "io.circe" %% "circe-yaml" % "0.13.1"
)

sys.props.get("plugin.version") match {
  case Some(v) => addSbtPlugin("com.kiemlicz" % "shelm" % v)
  case _ => sys.error(
      """|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin
    )
}
