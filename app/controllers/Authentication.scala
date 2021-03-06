package controllers

import org.apache.commons.codec.digest.DigestUtils
import play.api.mvc._

import scala.concurrent.Future

package authentication {

  abstract class Secure(db: DB) extends ActionBuilder[Request] with ActionFilter[Request] {

    protected def authorized(authHeader: String): Boolean = {
      val (user, pass) = decodeBasicAuth(authHeader)
      db.pass(user).contains(DigestUtils.sha1Hex(pass))
    }

    def getRole[A](request: Request[A]): Option[String] = {
      token(request).flatMap {
        t =>
          val (user, _) = decodeBasicAuth(t)
          db.getUserRole(user)
      }
    }

    private[this] def token[A](request: Request[A]): Option[String] = request.headers.get("Authorization")

    private[this] def decodeBasicAuth(authHeader: String): (String, String) = {
      val baStr = authHeader.replaceFirst("Basic ", "")
      val decoded = new sun.misc.BASE64Decoder().decodeBuffer(baStr)
      new String(decoded) match {
        case s if s.split(":").length != 2 => ("", "")
        case str =>
          val Array(user, password) = str.split(":")
          (user, password)
      }
    }
  }

  class SecureView(db: DB, defaultResult: Result) extends Secure(db) {
    def filter[A](request: Request[A]): Future[Option[Result]] = {
      val result = request.headers.get("Authorization") filter authorized
      Future.successful(if (result.isDefined) None else Some(defaultResult))
    }
  }

  class SecureBackEnd(db: DB) extends Secure(db) {
    private val unauthorized: Result = Results.Unauthorized.withHeaders("WWW-Authenticate" -> "Basic realm=Unauthorized")

    def filter[A](request: Request[A]): Future[Option[Result]] = {
      val result = request.headers.get("Authorization") filter authorized
      Future.successful(if (result.isDefined) None else Some(unauthorized))
    }
  }

}