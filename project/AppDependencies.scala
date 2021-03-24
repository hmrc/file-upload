import sbt._

private object AppDependencies {
  import play.sbt.PlayImport
  import play.core.PlayVersion

  private val akkaVersion = "2.6.10"

  val compile = Seq(
    "uk.gov.hmrc"              %% "mongo-lock"                % "7.0.0-play-28",
    PlayImport.ws,
    "uk.gov.hmrc"              %% "bootstrap-backend-play-28" % "4.1.0",
    "org.reactivemongo"        %% "reactivemongo-iteratees"   % "0.18.8",
    "com.typesafe.play"        %% "play-json-joda"            % "2.8.1",
    "com.typesafe.play"        %% "play-iteratees-reactive-streams" % "2.6.1",
    "com.google.code.findbugs" %  "jsr305"                    % "2.0.3"
  )

  val test = Seq(
    "com.vladsch.flexmark"   %  "flexmark-all"                % "0.35.10"           % "test, it",
    "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % "test,it",
    "uk.gov.hmrc"            %% "reactivemongo-test"          % "5.0.0-play-28"     % "test,it",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "5.1.0"             % "test,it",
    "com.typesafe.akka"      %% "akka-testkit"                % akkaVersion         % "test",
    "org.mockito"            %% "mockito-scala"               % "1.10.1"            % "test",
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"             % "test",
    "com.github.tomakehurst" %  "wiremock"                    % "1.58"              % "it"
  )

  val libraryDependencies = compile ++ test
}
