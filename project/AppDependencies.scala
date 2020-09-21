import sbt._

private object AppDependencies {
  import play.sbt.PlayImport
  import play.core.PlayVersion

  private val playBootstrapVersion = "2.25.0"
  private val domainVersion = "5.10.0-play-27"
  private val hmrcTestVersion = "3.9.0-play-27"
  private val akkaVersion = "2.5.26"
  private val authClientVersion = "3.1.0-play-27"

  val compile = Seq(
    "uk.gov.hmrc"              %% "mongo-lock"                % "6.23.0-play-27",
    PlayImport.ws,
    "uk.gov.hmrc"              %% "bootstrap-backend-play-27" % playBootstrapVersion,
    "uk.gov.hmrc"              %% "auth-client"               % authClientVersion,
    "uk.gov.hmrc"              %% "domain"                    % domainVersion,
    "com.typesafe.akka"        %% "akka-actor"                % akkaVersion,
    "com.typesafe.akka"        %% "akka-testkit"              % akkaVersion,
    "org.typelevel"            %% "cats"                      % "0.9.0",
    "org.reactivemongo"        %% "reactivemongo-iteratees"   % "0.18.8",
    "com.typesafe.play"        %% "play-json-joda"            % "2.6.14",
    "com.typesafe.play"        %% "play-iteratees-reactive-streams" % "2.6.1",
    "com.google.code.findbugs" %  "jsr305"                    % "2.0.3"
  )

  val test = Seq(
    "org.scalatest"          %% "scalatest"                   % "3.0.5"             % "test,it",
    "org.pegdown"            %  "pegdown"                     % "1.6.0"             % "test,it",
    "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % "test,it",
    "uk.gov.hmrc"            %% "reactivemongo-test"          % "4.21.0-play-27"    % "test,it",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "4.0.3"             % "test,it",
    "com.typesafe.akka"      %% "akka-testkit"                % akkaVersion         % "test",
    "org.mockito"            %  "mockito-core"                % "2.21.0"            % "test",
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"             % "test",
    "com.github.tomakehurst" %  "wiremock"                    % "1.58"              % "it"
  )

  val libraryDependencies = compile ++ test
}
