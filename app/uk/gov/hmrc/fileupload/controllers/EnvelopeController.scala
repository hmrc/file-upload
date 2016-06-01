package uk.gov.hmrc.fileupload.controllers

import play.api.mvc.Action
import uk.gov.hmrc.play.microservice.controller.BaseController

object EnvelopeController extends EnvelopeController {

}

trait EnvelopeController extends BaseController {

  def create = Action.async { implicit request =>
    ???
  }
}
