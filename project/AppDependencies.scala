import play.sbt.PlayImport
import play.core.PlayVersion
import sbt._

private object AppDependencies {

  private val bootstrapPlayVersion = "8.6.0"
  private val mongoVersion         = "1.9.0"

  val compile = Seq(
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-28"          % mongoVersion,
    PlayImport.ws,
    "uk.gov.hmrc"            %% "bootstrap-backend-play-28"   % bootstrapPlayVersion,
    "com.typesafe.play"      %% "play-json-joda"              % "2.8.1",
    "com.typesafe.play"      %% "play-iteratees-reactive-streams" % "2.6.1", // not available for Scala 2.13
    "org.typelevel"          %% "cats-core"                   % "2.10.0",
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"      % bootstrapPlayVersion    % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"     % mongoVersion            % Test,
    "org.mockito"            %% "mockito-scala-scalatest"     % "1.16.46"               % Test,
    "com.typesafe.akka"      %% "akka-testkit"                % PlayVersion.akkaVersion % Test
  )

  val libraryDependencies = compile ++ test

  val it = Seq.empty
}
