/*
fixme revert
sys.props.get("plugin.version") match {
  case Some(v) => addSbtPlugin("io.github.kiemlicz" % "shelm" % z)
  case _ => sys.error(
    """|The system property 'plugin.version' is not defined.
       |Specify this property using the scriptedLaunchOpts -D.""".stripMargin
  )
}
*/
addSbtPlugin("io.github.kiemlicz" % "shelm" % "0.4.3-2-bc8efdd-dirty-SNAPSHOT")