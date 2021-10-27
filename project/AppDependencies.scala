import play.core.PlayVersion.akkaVersion
import sbt._

private object AppDependencies {
  import play.sbt.PlayImport
  import play.core.PlayVersion

  private val bootstrapPlayVersion = "5.16.0"
  private val mongoVersion = "0.55.0"

  val compile = Seq(
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-28"          % mongoVersion,
    PlayImport.ws,
    "uk.gov.hmrc"            %% "bootstrap-backend-play-28"   % bootstrapPlayVersion,
    "com.typesafe.play"      %% "play-json-joda"              % "2.8.1",
    "com.typesafe.play"      %% "play-iteratees-reactive-streams" % "2.6.1"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"      % bootstrapPlayVersion % "test,it",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"     % mongoVersion        % "test,it",
    "com.typesafe.akka"      %% "akka-testkit"                % akkaVersion         % "test",
    "org.mockito"            %% "mockito-scala-scalatest"     % "1.16.46"           % "test,it",
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"             % "test"
  )

  val libraryDependencies = compile ++ test
}
