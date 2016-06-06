package uk.gov.hmrc.fileupload.actors

import akka.actor.{Props, Actor}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.actors.IdGenerator.NextId

object IdGenerator {
	def props: Props = Props[IdGenerator]

	case object NextId
}

class IdGenerator extends Actor {

	override def receive = {
		case NextId => sender() ! BSONObjectID.generate
	}
}
