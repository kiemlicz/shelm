logLevel := Level.Warn
resolvers += Resolver.bintrayIvyRepo("rallyhealth", "sbt-plugins")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")
addSbtPlugin("com.rallyhealth.sbt" % "sbt-git-versioning" % "1.4.0")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6")