package models

import play.api.db.slick.DB
import play.api.db.slick.Config.driver.simple._
import play.api.Play.current
import scala.slick.jdbc.{ GetResult, StaticQuery => Q }
import securesocial.core.Identity

object Implicits {
  implicit def identity2user(x: Identity): User = x match {
    case u: User => u
    case other => throw new RuntimeException("Identity is not user: "+ other) /* Throwing should be graceful enough as this should NEVER happen */
  }
}

/**
 * Helper for pagination.
 */
case class Page[A](items: Seq[A], page: Int, offset: Long, total: Long) {
  lazy val prev = Option(page - 1).filter(_ >= 0)
  lazy val next = Option(page + 1).filter(_ => (offset + items.size) < total)
}

trait StandardTable {
  /* Not very pretty but will do for now to decrease duplication with AutoInc */
  def getNextId(seqName: String) = DB.withSession { implicit db: Session =>
    Some((Q[Long] + "select nextval('" + seqName + "_seq') ").first)
  }
}