package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.ws.WS
import play.api.libs.concurrent._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

import securesocial.core.SecureSocial

import views._
import models._

class LockIpUnknownException extends RuntimeException

object Lock extends Controller with Secured with SecureSocial {

  /* These variables should probably be moved into a model and use Cache */
  @volatile var ip: Option[String] = None
  @volatile var needsReset = false
  var lastCheck: Option[java.util.Date] = None

  def refreshAction() = SecuredAction(WithAccess(_.admin == true)) { implicit request =>
    Async {
      refresh.map { res =>
        res match {
          case true => Ok("Yes")
          case false => Ok("No")
        }
      }
    }
  }

  def openAction() = SecuredAction(WithAccess(_.canOpen == true)) { implicit request =>
    try {
      Async { open().map(res => Ok(html.openLock(res))) }
    } catch {
      case _: LockIpUnknownException =>
        Logger.warn("Lock open was requested via web but lock IP unknown")
        Ok(html.openLock(false))
    }
  }

  def verify(revision: Int) = withSecret { implicit request =>
    lastCheck = Some(new java.util.Date())
    if (revision == 0) needsReset = false /* If revision is 0 it means that we just started up, so no need to reset again */

    if (needsReset) {
      needsReset = false
      ResetContent /* Lock needs a reset */
    } else if (revision == Tag.revision) Ok /* Secret good, revision good */
    else PartialContent /* Revision incorrect, refresh tag list */
  }

  /* Helpers */
  /** Sends a refresh request to the lock (the lock will do a new check, realize that the revision is off and download the new keylist). The return status of this method does not indicate weather a download was actually completed, just that the refresh request was made */
  def refresh() = WS.url("http://" + ip.getOrElse(throw new LockIpUnknownException) + ":" + current.configuration.getString("lock.port").getOrElse("9001") + "/r").get().orTimeout("Oops", 5000).map { result =>
    result.fold(
      response => response.body.stripLineEnd == "OK",
      timeout => false)
  } recover {
    case _: java.net.ConnectException =>
      Logger.error("Connection refused to lock")
      needsReset = true
      false
  }

  /** Opens the lock */
  def open(openSecret: String = current.configuration.getString("lock.opensecret").get) =
    WS.url("http://" + ip.getOrElse(throw new LockIpUnknownException) + ":" + current.configuration.getString("lock.port").getOrElse("9001") + "/o/" + openSecret).get().orTimeout("Oops", 10000).map { result =>
      result.fold(
        response => {
          Logger.info("Lock opened from web request")
          Enter.logAccess(Tag.defaultTag)
          response.body.stripLineEnd == "OK"
        },
        timeout => {
          needsReset = true
          false
        })
    } recover {
      case _: java.net.ConnectException =>
        Logger.error("Connection refused to lock")
        needsReset = true
        false
    }
}

trait Secured {
  /* Helper */
  def withSecret(f: Request[AnyContent] => Result) = Action { request =>
    if (request.getQueryString("s").getOrElse("") != current.configuration.getString("lock.secret").get) {
      Logger.error("Access from lock with incorrect secret attempted")
      Results.Unauthorized
    } else {
      Lock.ip = Some(request.headers.get("x-forwarded-for").getOrElse(request.remoteAddress))
      f(request)
    }
  }
}