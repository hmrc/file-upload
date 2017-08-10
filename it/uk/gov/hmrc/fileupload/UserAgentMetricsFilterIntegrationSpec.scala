package uk.gov.hmrc.fileupload

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.codahale.metrics.{MetricRegistry, Timer}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import play.api.http.HeaderNames
import play.api.mvc.Action
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.filters.{UserAgent, UserAgentRequestFilter}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class UserAgentMetricsFilterIntegrationSpec extends FunSuite with BeforeAndAfterAll with Matchers with Eventually {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  private val dfsFrontend = "dfs-frontend"
  val whitelist =
    Set(dfsFrontend, "voa-property-linking-frontend").map(UserAgent.apply)

  implicit val patience: PatienceConfig = PatienceConfig(5.seconds, 1.second)

  test("Timer created for User-Agent when header is white listed") {
    val metrics = new MetricRegistry
    val filter = new UserAgentRequestFilter(metrics, whitelist)

    val rh = FakeRequest().withHeaders(HeaderNames.USER_AGENT -> dfsFrontend)
    val endAction = Action(Ok("boom"))
    filter(endAction)(rh).run()

    eventually {
      metrics.timer(s"request.user-agent.$dfsFrontend").getCount shouldBe 1
    }

    filter(endAction)(rh).run()

    eventually {
      metrics.timer(s"request.user-agent.$dfsFrontend").getCount shouldBe 2
    }
  }

  test("Timer for NoUserAgent is incremented when User-Agent header is missing") {
    val metrics = new MetricRegistry
    val filter = new UserAgentRequestFilter(metrics, whitelist)

    val rh = FakeRequest()
    val endAction = Action(Ok("boom"))
    filter(endAction)(rh).run()

    eventually {
      metrics.timer(s"request.user-agent.NoUserAgent").getCount shouldBe 1
    }
  }

  test("Timer for UnknownUserAgent is incremented when User-Agent header not in whitelist") {
    val metrics = new MetricRegistry
    val filter = new UserAgentRequestFilter(metrics, whitelist)

    val rh = FakeRequest().withHeaders(HeaderNames.USER_AGENT -> UUID.randomUUID().toString)
    val endAction = Action(Ok("boom"))
    filter(endAction)(rh).run()

    eventually {
      metrics.timer(s"request.user-agent.UnknownUserAgent").getCount shouldBe 1
    }
  }

}
