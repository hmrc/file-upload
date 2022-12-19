import play.sbt.PlayImport.PlayKeys
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.DefaultBuildSettings

lazy val microservice = Project("file-upload", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(majorVersion := 2)
  .settings(PlayKeys.playDefaultPort := 8898)
  .settings(SbtDistributablesPlugin.publishingSettings: _*)
  .settings(
    scalaVersion := "2.12.17",
    libraryDependencies ++= AppDependencies.libraryDependencies,
    Test / parallelExecution := false,
    scalacOptions += "-Wconf:src=routes/.*:s"
  )
  .configs(IntegrationTest)
  .settings(DefaultBuildSettings.integrationTestSettings())
