import sbt._

private object AppDependencies {
  import play.sbt.PlayImport
  import play.core.PlayVersion

  private val akkaVersion = "2.6.10"

  val compile = Seq(
    "uk.gov.hmrc.mongo"        %% "hmrc-mongo-play-28"        % "0.51.0",
    PlayImport.ws,
    "uk.gov.hmrc"              %% "bootstrap-backend-play-28" % "5.11.0",
    "com.typesafe.play"        %% "play-json-joda"            % "2.8.1",
    "com.typesafe.play"        %% "play-iteratees-reactive-streams" % "2.6.1",
    "com.google.code.findbugs" %  "jsr305"                    % "2.0.3"
  )

  val test = Seq(
    "com.vladsch.flexmark"   %  "flexmark-all"                % "0.35.10"           % "test, it",
    "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % "test,it",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"     % "0.47.0"            % "test,it",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "5.1.0"             % "test,it",
    "com.typesafe.akka"      %% "akka-testkit"                % akkaVersion         % "test",
    "org.mockito"            %% "mockito-scala"               % "1.10.1"            % "test",
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"             % "test",
    "com.github.tomakehurst" %  "wiremock"                    % "1.58"              % "it"
  )

  val libraryDependencies = compile ++ test
}
