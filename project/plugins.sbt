resolvers += Resolver.bintrayIvyRepo("hmrc", "sbt-plugin-releases")
resolvers += Resolver.bintrayRepo("hmrc", "releases")

resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.13.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "1.15.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "1.2.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.5")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.19")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-bobby" % "0.36.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory" % "0.17.0")
