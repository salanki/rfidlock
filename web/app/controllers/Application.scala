package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.Play.current

import securesocial.core.{ SecureSocial, Identity, Authorization }

import anorm._

import views._
import models._
import models.Implicits._

object Application extends Controller with Secured with SecureSocial {

  /**
   * This result directly redirect to the application home.
   */
  val Home = Redirect(routes.Application.list(0, -2, 0, 2, ""))

  /**
   * Describe the tag form (used in both edit and create screens).
   */
  val tagForm = Form(
    mapping(
      "id" -> ignored(NotAssigned: Pk[Long]),
      "name" -> nonEmptyText,
      "added" -> ignored(null: java.util.Date),
      "enabled" -> boolean, //default(boolean, true),
      "tuid" -> nonEmptyText)(Tag.apply)(Tag.unapply))

  // -- Actions

  /**
   * Handle default path requests, redirect to computers list
   */
  def index = Action { Home }

  /**
   * Display the paginated list of computers.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param filter Filter applied on computer names
   */
  def list(entryPage: Int, entryOrderBy: Int, tagPage: Int, tagOrderBy: Int, filter: String) = SecuredAction(WithAccess(_.admin == true)) { implicit request =>
    Ok(html.list(
      Entry.list(page = entryPage, orderBy = entryOrderBy, filter = ("%" + filter + "%")),
      entryOrderBy,
      Tag.list(page = tagPage, orderBy = tagOrderBy, filter = ("%" + filter + "%")),
      tagOrderBy,
      filter, Lock.lastCheck))
  }

  /**
   * Display the 'edit form' of a existing Tag.
   *
   * @param id Id of the tag to edit
   */
  def edit(id: Long) = SecuredAction(WithAccess(_.admin == true)) { implicit request =>
    Tag.findById(id).map { tag =>
      Ok(html.editForm(id, tagForm.fill(tag)))
    }.getOrElse(NotFound)
  }

  /**
   * Handle the 'edit form' submission
   *
   * @param id Id of the tag to edit
   */
  def update(id: Long) = SecuredAction(WithAccess(_.admin == true)) { implicit request =>
    tagForm.bindFromRequest.fold(
      formWithErrors => BadRequest(html.editForm(id, formWithErrors)),
      tag => {
        backgroundRefreshLock()
        Tag.update(id, tag)
        Home.flashing("success" -> "Tag %s has been updated".format(tag.name))
      })
  }

  /**
   * Display the 'new tag form'.
   */
  def create = SecuredAction(WithAccess(_.admin == true)) { implicit request =>
    Ok(html.createForm(tagForm))
  }

  /**
   * Handle the 'new tag form' submission.
   */
  def save = SecuredAction(WithAccess(_.admin == true)) { implicit request =>
    tagForm.bindFromRequest.fold(
      formWithErrors => BadRequest(html.createForm(formWithErrors)),
      tag => {
        backgroundRefreshLock()
        Tag.insert(tag)
        Home.flashing("success" -> "Tag %s has been added".format(tag.name))
      })
  }

  /**
   * Handle tag deletion.
   */
  def delete(id: Long) = SecuredAction(WithAccess(_.admin == true)) { implicit request =>
    if (id == 1) NotFound
    else {
      Tag.delete(id)
      Home.flashing("success" -> "Tag has been deleted")
    }
  }

  /**
   * List all enabled tags in a format that the lock can read.
   * Maybe this should be moved to another controller
   */
  def listTagsToLock = withSecret { implicit request =>
    val tagList = Tag.listEnabled.map(_.tuid)

    val responseSet = Tag.revision +: tagList ++: "" +: "."

    Logger.info("Sending: " + tagList.size + " tags to lock")
    Ok(responseSet.mkString("\n"))
  }

  /* Helpers */
  private def backgroundRefreshLock() = {
    import play.api.libs.concurrent.Execution.Implicits._

    /* Until Arduino fixes the TCP stack of the WiFi shield we do not want to do this 
    concurrent.Future {
      try {
      Lock.refresh().map(res => res match {
        case true => Logger.info("Lock refreshed")
        case false => Logger.warn("Lock failed to refresh")
      })      
    } catch {
      case _: LockIpUnknownException => Logger.warn("Lock IP Unknown for refresh")
    }
    
    } */
    Logger.info("Lock refresh disabled")

  }
}

/* Authenticator helpers */
case class WithAccess(authFun: User => Boolean) extends Authorization {
    def isAuthorized(user: Identity) = authFun(user)
}
  
