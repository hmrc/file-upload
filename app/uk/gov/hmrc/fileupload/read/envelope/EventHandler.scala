package uk.gov.hmrc.fileupload.read.envelope

import akka.actor.{Actor, Props}

class EventHandler extends Actor {

  override def receive = {
    case _ => println("nothing implemented yet")
  }

}

object EventHandler {

  def props = Props(new EventHandler())
}
