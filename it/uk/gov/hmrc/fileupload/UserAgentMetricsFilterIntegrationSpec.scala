package uk.gov.hmrc.fileupload

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.codahale.metrics.MetricRegistry
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite
import play.api.http.HeaderNames
import play.api.mvc.Action
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.filters.{UserAgent, UserAgentRequestFilter}

import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.JavaConverters._

class UserAgentMetricsFilterIntegrationSpec
  extends AnyFunSuite
     with BeforeAndAfterAll
     with Matchers
     with Eventually
     with IntegrationPatience {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  val dfsFrontend = "dfs-frontend"
  val allowlist =
    Set(dfsFrontend, "voa-property-linking-frontend").map(UserAgent.apply)

  val nginxChecks = "nginx-health"
  val denylist =
    Set(nginxChecks).map(UserAgent.apply)

  def withFilter[T](block: (UserAgentRequestFilter, MetricRegistry) => T): T = {
    val metrics = new MetricRegistry
    val filter = new UserAgentRequestFilter(metrics, allowlist, denylist)
    block(filter, metrics)
  }

  val endAction = Action(Ok("boom"))

  def grabTimerCounts(metrics: MetricRegistry): Map[String, Long] =
    metrics.getTimers.asScala.map { case (name, timer) =>
      name -> timer.getCount
    }.toMap

  test("Timer created for User-Agent when header is allow listed") {
    withFilter { (filter, metrics) =>
      val rh = FakeRequest().withHeaders(HeaderNames.USER_AGENT -> dfsFrontend)
      filter(endAction)(rh).run()

      eventually {
        metrics.timer(s"request.user-agent.$dfsFrontend").getCount shouldBe 1
      }

      filter(endAction)(rh).run()

      eventually {
        grabTimerCounts(metrics) should contain(s"request.user-agent.$dfsFrontend" -> 2)
      }
    }
  }

  test("Timer for NoUserAgent is incremented when User-Agent header is missing") {
    withFilter { (filter, metrics) =>
      val rh = FakeRequest()
      filter(endAction)(rh).run()

      eventually {
        grabTimerCounts(metrics) should contain("request.user-agent.NoUserAgent" -> 1)
      }
    }
  }

  test("Timer for UnknownUserAgent is incremented when User-Agent header not in allowlist") {
    withFilter { (filter, metrics) =>
      val rh = FakeRequest().withHeaders(HeaderNames.USER_AGENT -> UUID.randomUUID().toString)
      filter(endAction)(rh).run()

      eventually {
        grabTimerCounts(metrics) should contain ("request.user-agent.UnknownUserAgent" -> 1)
      }
    }
  }

  test("Timer for User-Agent in ignore list is.. ignored") {
    withFilter { (filter, metrics) =>
      val rh = FakeRequest().withHeaders(HeaderNames.USER_AGENT -> nginxChecks)
      filter(endAction)(rh).run()
      metrics.getTimers.size() shouldBe 0
    }
  }
}
