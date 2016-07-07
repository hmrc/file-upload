package uk.gov.hmrc.fileupload.controllers

//import java.io.File
//
//import org.specs2.specification.{Step, Fragments}
//import play.api.test.PlaySpecification
//
//import scala.concurrent.{Await, Future}
//import scala.concurrent.duration._
//
///**
//	* Created by jay on 27/06/2016.
//	*/
//trait Server {
//	self: PlaySpecification =>
//
//	val support = new FileUploadSupport
//
//	lazy val workspace = {
//		val loc = classOf[Server].getProtectionDomain.getCodeSource.getLocation.getFile
//		val path = loc.substring(loc.indexOf(":")+1, loc.indexOf("target"))
//		new File(path)
//	}
//
//	val ostream = new File(s"test-${self.getClass.getName}.log")
//
//	var process: Option[Process] = None
//
//	override def map(fragments: => Fragments) =
//		Step(start) ^ fragments ^ Step(stop)
//
//	def start = {
//		process = waitForStartup
//	}
//
//	def stop = {
//		process foreach{
//			p => p.destroy()
//			p.waitFor()
//		}
//	}
//
//	def startProcess =
//		new ProcessBuilder("sbt", "run")
//			.directory(workspace)
//			.redirectErrorStream(true)
//			.redirectOutput(ostream)
//		.start()
//
//	def waitForStartup =
//		Await.result(
//			Future{
//				val proc = Some(startProcess)
//				while(!support.appIsAlive){ Thread.sleep(500)}
//				proc
//			}, 1 minute)
//}
