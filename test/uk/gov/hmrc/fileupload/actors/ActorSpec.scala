package uk.gov.hmrc.fileupload.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, DefaultTimeout, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/**
  * Created by Josiah on 6/4/2016.
  */
abstract class ActorSpec extends TestKit(ActorSystem("TestActorSpec"))
  with DefaultTimeout with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  override protected def afterAll(): Unit = shutdown()
}