import play.sbt.PlayImport
import play.core.PlayVersion
import sbt._

private object AppDependencies {

  private val bootstrapPlayVersion = "9.10.0"
  private val mongoVersion         = "2.5.0"

  val compile = Seq(
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-30"          % mongoVersion,
    PlayImport.ws,
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30"   % bootstrapPlayVersion,
    "org.playframework"      %% "play-json-joda"              % "3.0.4",
    "org.typelevel"          %% "cats-core"                   % "2.13.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"      % bootstrapPlayVersion      % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30"     % mongoVersion              % Test,
    "org.apache.pekko"       %% "pekko-testkit"               % PlayVersion.pekkoVersion  % Test
  )

  val libraryDependencies = compile ++ test

  val it = Seq.empty
}
