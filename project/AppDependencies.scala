import sbt._

private object AppDependencies {
  import play.sbt.PlayImport
  import play.core.PlayVersion

  private val microserviceBootstrapVersion = "10.6.0"
  private val domainVersion = "5.2.0"
  private val hmrcTestVersion = "3.3.0"
  private val akkaVersion = "2.5.18"
  private val authClientVersion = "2.27.0-play-25"

  val compile = Seq(
    "uk.gov.hmrc"              %% "mongo-lock"              % "6.23.0-play-25",
    PlayImport.ws,
    "uk.gov.hmrc"              %% "microservice-bootstrap"  % microserviceBootstrapVersion,
    "uk.gov.hmrc"              %% "auth-client"             % authClientVersion,
    "uk.gov.hmrc"              %% "domain"                  % domainVersion,
    "com.typesafe.akka"        %% "akka-actor"              % akkaVersion,
    "com.typesafe.akka"        %% "akka-testkit"            % akkaVersion,
    "org.typelevel"            %% "cats"                    % "0.7.0",
    "org.reactivemongo"        %% "reactivemongo-iteratees" % "0.18.8",
    "com.typesafe.play"        %% "play-iteratees"          % "2.5.9" force(),
    "com.google.code.findbugs" %  "jsr305"                  % "2.0.3"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "hmrctest"                    % hmrcTestVersion     % "test,it",
    "org.scalatest"          %% "scalatest"                   % "3.0.5"             % "test,it",
    "org.pegdown"            %  "pegdown"                     % "1.6.0"             % "test,it",
    "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % "test,it",
    "uk.gov.hmrc"            %% "reactivemongo-test"          % "4.21.0-play-25"    % "test,it",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "2.0.1"             % "test,it",
    "com.typesafe.akka"      %% "akka-testkit"                % akkaVersion         % "test",
    "org.mockito"            %  "mockito-core"                % "2.21.0"            % "test",
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"             % "test",
    "com.github.tomakehurst" %  "wiremock"                    % "1.58"              % "it"
  )

  def apply() = compile ++ test
}
