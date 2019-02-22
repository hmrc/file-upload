import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "file-upload"
  
  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {
  import play.sbt.PlayImport._
  import play.core.PlayVersion

  private val microserviceBootstrapVersion = "10.4.0"
  private val domainVersion = "5.2.0"
  private val hmrcTestVersion = "3.2.0"
  private val playReactivemongoVersion = "6.4.0"
  private val akkaVersion = "2.4.10"
  private val catsVersion = "0.7.0"
  private val authClientVersion = "2.17.0-play-25"

  val compile = Seq(
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion,
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "auth-client" % authClientVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "com.typesafe.akka" % "akka-actor_2.11" % akkaVersion,
    "com.typesafe.akka" % "akka-testkit_2.11" % akkaVersion,
    "org.typelevel" %% "cats" % catsVersion,
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
        "org.pegdown" % "pegdown" % "1.5.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "3.1.0" % scope,
        "com.typesafe.akka" % "akka-testkit_2.11" % akkaVersion % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope
      )
    }.test
  }

  object IntegrationTest {
    def apply() = new TestDependencies {

      override lazy val scope: String = "it"

      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % "2.2.6" % scope,
        "org.pegdown" % "pegdown" % "1.5.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope,
        "com.github.tomakehurst" % "wiremock" % "1.58" % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "3.1.0" % scope
      )
    }.test
  }

  def apply() = compile ++ Test() ++ IntegrationTest()
}
