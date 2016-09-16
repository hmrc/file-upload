package uk.gov.hmrc.fileupload.read.file

import cats.data.Xor
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.fileupload.{FileId, FileRefId}
import uk.gov.hmrc.fileupload.read.envelope.Envelope

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object Service {

  type GetFileResult = GetFileError Xor FileFound
  case class FileFound(name: Option[String] = None, length: Long, data: Enumerator[Array[Byte]])
  sealed trait GetFileError
  case object GetFileNotFoundError extends GetFileError

  def retrieveFile(getFileFromRepo: FileRefId => Future[Option[FileData]])
                  (envelope: Envelope, fileId: FileId)
                  (implicit ex: ExecutionContext): Future[GetFileResult] =
    (for {
      file <- envelope.getFileById(fileId)
    } yield {
      getFileFromRepo(file.fileRefId).map { maybeData =>
        val fileWithClientProvidedName = maybeData.map { d => FileFound(file.name, d.length, d.data) }
        Xor.fromOption(fileWithClientProvidedName, ifNone = GetFileNotFoundError)
      }
    }).getOrElse(Future.successful(Xor.left(GetFileNotFoundError)))

}
