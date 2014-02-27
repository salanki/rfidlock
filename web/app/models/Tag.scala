package models

import java.util.{ Date }

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

case class Tag(id: Pk[Long] = NotAssigned, name: String, added: Date, enabled: Boolean, tuid: String)

object Tag {
  /* A pretty ugly way to keep some sort of "haschanged" discriminator for the lock when looking for new data */
  @volatile var revision = 1
   
  def increaseRevision() = {
    if(revision >= 100) {
      revision = 1
    } else revision = revision+1
  }
  // -- Parsers

  /**
   * Parse an Entry from a ResultSet
   */
  val simple = {
    get[Pk[Long]]("tag.id") ~
      get[String]("tag.name") ~
      get[Date]("tag.added") ~
      get[Boolean]("tag.enabled") ~
      get[String]("tag.tuid") map {
        case id ~ name ~ added ~ enabled ~ tuid => Tag(id, name, added, enabled, tuid)
      }
  }

  // -- Queries
  def defaultTag = Tag.findById(1).get

  /**
   * Retrieve a tag from the id.
   */
  def findById(id: Long): Option[Tag] = {
    DB.withConnection { implicit connection =>
      SQL("SELECT * FROM tag WHERE id = {id}").on('id -> id).as(Tag.simple.singleOpt)
    }
  }

  /**
   * Retrieve a tag from the uid.
   */
  def findByUid(id: String): Option[Tag] = {
    DB.withConnection { implicit connection =>
      SQL("SELECT * FROM tag WHERE tuid = {id}").on('id -> id).as(Tag.simple.singleOpt)
    }
  }

  /**
   * Lists enabled tags
   */
  def listEnabled: Seq[Tag] = {
    DB.withConnection { implicit connection =>

      SQL(
        """
          SELECT * FROM tag
          WHERE tag.enabled = TRUE
        """).as(Tag.simple *)
    }
  }

  /**
   * Return a page of Tag.
   *
   * @param page Page to display
   * @param pageSize Number of entries per page
   * @param orderBy Entry property used for sorting
   * @param filter Filter applied on the name column
   */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%"): Page[Tag] = {

    val offest = pageSize * page
    val mode = if(orderBy < 0) "DESC" else "ASC"

    DB.withConnection { implicit connection =>

      val entries = SQL(
        """
          SELECT * FROM tag
          WHERE tag.name LIKE {filter}
          ORDER BY %d %s nulls LAST
          LIMIT {pageSize} OFFSET {offset}
        """.format(scala.math.abs(orderBy), mode)).on(
          'pageSize -> pageSize,
          'offset -> offest,
          'filter -> filter).as(Tag.simple *)
      
      val totalRows = SQL(
        """
          SELECT COUNT(*) FROM tag 
    	  WHERE tag.name ILIKE {filter}
        """).on(
          'filter -> filter).as(scalar[Long].single)

      Page(entries, page, offest, totalRows)
    }

  }

  /**
   * Update a tag.
   *
   * @param id The computer id
   * @param tag The tag values.
   */
  def update(id: Long, tag: Tag) = {
    increaseRevision()
    
    DB.withConnection { implicit connection =>
      SQL(
        """
          UPDATE tag
          SET name = {name}, enabled = {enabled}, tuid = {tuid}
          WHERE id = {id}
        """).on(
          'id -> id,
          'name -> tag.name,
          'enabled -> tag.enabled,
          'tuid -> tag.tuid).executeUpdate()
    }
  }

  /**
   * Insert a new tag.
   *
   * @param tag The tag values.
   */
  def insert(tag: Tag) = {
    increaseRevision()
    
    DB.withConnection { implicit connection =>
      SQL(
        """
          INSERT INTO tag (name,added,enabled,tuid) VALUES (
            {name}, NOW(), {enabled}, {tuid}
          )
        """).on(
          'name -> tag.name,
          'enabled -> tag.enabled,
          'tuid -> tag.tuid).executeUpdate()
    }
  }

  /**
   * Delete a tag.
   *
   * @param id Id of the tag to delete.
   */
  def delete(id: Long) = {
    increaseRevision()
    
    DB.withConnection { implicit connection =>
      SQL("DELETE FROM tag WHERE ID = {id}").on('id -> id).executeUpdate()
    }
  }
}