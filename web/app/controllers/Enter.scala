package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.libs.Akka
import play.libs.Akka._
import play.api.libs.concurrent._
import play.api.libs.concurrent.Execution.Implicits._

import akka.actor.{ Props, ActorSystem, Actor, ActorRef }
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import anorm._

import views._
import models._

/* Handle scanning for registration of new tags */
case object BeginScan
case object InScan
case object NoScan
case class Scan(tuid: String)

class ScanActor extends Actor {
  var active: Option[ActorRef] = None

  def receive = {
    case BeginScan =>
      active = Some(sender)
      Logger.info("Beginning card scan")
    case NoScan =>
      active = None
      Logger.info("Cancelling scan")
    case scan @ Scan(tuid) => active match {
      case Some(requestor) =>
        Logger.info("Got scan while requested: " + tuid)
        requestor ! scan
        sender ! InScan
      case None =>
        sender ! NoScan
    }
  }
}

object Enter extends Controller with Secured {
  val scanActor = Akka.system.actorOf(Props[ScanActor], name = "scanActor")

  def scan = Action {
    val timeoutFuture = Promise.timeout("timeout", 28.seconds)
    Async {
      implicit val timeout = Timeout(30 seconds)

      scala.concurrent.Future.firstCompletedOf(Seq(timeoutFuture, (scanActor ? BeginScan))).map { response =>
        response match {
          case "timeout" =>
            scanActor ! NoScan
            Ok("timeout")
          case Scan(tuid) =>
            scanActor ! NoScan
            Ok(tuid)
        }
      }
    }
  }

  def log(tuid: String) = Action {
    Tag.findByUid(tuid) match {
      case Some(tag) =>
        logAccess(tag)
        Logger.info("Logged access for: " + tag.name)
        Ok
      case None =>
        Logger.error("Tag not found: " + tuid)
        BadRequest
    }
  }

  def authorize(tuid: String) = Action {
    Tag.findByUid(tuid) match {
      case Some(tag) =>
        tag.enabled match {
          case true =>
            Logger.info("Permitting request for: " + tuid)
            logAccess(tag)
            Ok
          case false =>
            Logger.info("Denying request (tag disabled) for: " + tuid)
            logAccess(tag, false)
            NotFound
        }
      case None =>
        Async {
          implicit val timeout = Timeout(2 seconds)
          (scanActor ? Scan(tuid)).map { response =>
            response match {
              case InScan =>
              case NoScan =>
                Logger.info("Denying request (tag not exist) for: " + tuid)
                logAccess(Tag.defaultTag, false)
            }
            NotFound
          }
        }
    }
  }

  /* Helpers */
  def logAccess(tag: Tag, permitted: Boolean = true) = Entry.insert(Entry(NotAssigned, new java.util.Date, permitted, tag.id.get))
}
            
