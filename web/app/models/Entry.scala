package models

import java.util.{ Date }

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

case class Entry(id: Pk[Long] = NotAssigned, date: Date, permitted: Boolean, tagId: Long)

object Entry {

  // -- Parsers

  /**
   * Parse an Entry from a ResultSet
   */
  val simple = {
    get[Pk[Long]]("entry.id") ~
      get[Date]("entry.date") ~
      get[Boolean]("entry.permitted") ~
      get[Long]("entry.tag_id") map {
        case id ~ date ~ permitted ~ tagId => Entry(id, date, permitted, tagId)
      }
  }

  /**
   * Parse a (Entry,Tag) from a ResultSet
   */
  val withTag = Entry.simple ~ Tag.simple map {
    case entry ~ tag => (entry, tag)
  }

  // -- Queries

  /**
   * Retrieve an entry from the id.
   */
  def findById(id: Long): Option[Entry] = {
    DB.withConnection { implicit connection =>
      SQL("SELECT * FROM entry WHERE id = {id}").on('id -> id).as(Entry.simple.singleOpt)
    }
  }

  /**
   * Return a page of (Entry,Tag).
   *
   * @param page Page to display
   * @param pageSize Number of entries per page
   * @param orderBy Entry property used for sorting
   * @param filter Filter applied on the name column
   */
  def list(page: Int = 0, pageSize: Int = 10, orderBy: Int = 1, filter: String = "%"): Page[(Entry, Tag)] = {

    val offest = pageSize * page
    
    val mode = if(orderBy < 0) "DESC" else "ASC"
    
    DB.withConnection { implicit connection =>

      val entries = SQL(
        """
          SELECT * FROM entry 
          LEFT JOIN tag ON entry.tag_id = tag.id
          WHERE tag.name LIKE {filter}
          ORDER BY %d %s nulls LAST
          LIMIT {pageSize} OFFSET {offset}
        """.format(scala.math.abs(orderBy), mode)).on(
          'pageSize -> pageSize,
          'offset -> offest,
          'filter -> filter).as(Entry.withTag *)

      val totalRows = SQL(
        """
          SELECT COUNT(*) FROM entry 
          LEFT JOIN tag ON entry.tag_id = tag.id
    	  WHERE tag.name ILIKE {filter}
        """).on(
          'filter -> filter).as(scalar[Long].single)

      Page(entries, page, offest, totalRows)

    }
  }

  /**
   * Insert a new entry.
   *
   * @param entry The entry values.
   */
  def insert(entry: Entry) = {
    DB.withConnection { implicit connection =>
      SQL(
        """
          INSERT INTO entry (date,permitted,tag_id) VALUES ( 
            {date}, {permitted}, {tag_id}
          )
        """).on(
          'date -> entry.date,
          'permitted -> entry.permitted,
          'tag_id -> entry.tagId).executeUpdate()
    }
  }

  /**
   * Delete an entry.
   *
   * @param id Id of the computer to delete.
   */
  def delete(id: Long) = {
    DB.withConnection { implicit connection =>
      SQL("DELETE FROM entry WHERE id = {id}").on('id -> id).executeUpdate()
    }
  }

}