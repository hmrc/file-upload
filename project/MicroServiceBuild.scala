import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "file-upload"
  
  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {
  import play.sbt.PlayImport._
  import play.core.PlayVersion

  private val microserviceBootstrapVersion = "10.6.0"
  private val domainVersion = "5.2.0"
  private val hmrcTestVersion = "3.3.0"
  private val simpleReactiveMongoVesion = "7.20.0-play-25"
  private val akkaVersion = "2.5.18"
  private val catsVersion = "0.7.0"
  private val authClientVersion = "2.27.0-play-25"
  import play.core.PlayVersion

  private val scalatestPlusPlayVersion = "2.0.1"
  private val pegdownVersion = "1.6.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactiveMongoVesion,
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "auth-client" % authClientVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "com.typesafe.akka" % "akka-actor_2.11" % akkaVersion,
    "com.typesafe.akka" % "akka-testkit_2.11" % akkaVersion,
    "org.typelevel" %% "cats" % catsVersion,
    "org.reactivemongo" %% "reactivemongo-iteratees" % "0.17.1",
    "com.typesafe.play" %% "play-iteratees" % "2.5.9" force(),
    "com.google.code.findbugs" % "jsr305" % "2.0.3")

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % "3.0.5" % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "4.15.0-play-25" % scope,
        "com.typesafe.akka" % "akka-testkit_2.11" % akkaVersion % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion % scope,
        "org.mockito" % "mockito-core" % "2.21.0" % scope,
        "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % scope
      )
    }.test
  }

  object IntegrationTest {
    def apply() = new TestDependencies {

      override lazy val scope: String = "it"

      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % "3.0.5" % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion % scope,
        "com.github.tomakehurst" % "wiremock" % "1.58" % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "4.15.0-play-25" % scope
      )
    }.test
  }

  def apply() = compile ++ Test() ++ IntegrationTest()
}
