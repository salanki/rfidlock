package models

import play.api.libs.Codecs

import play.api.db.slick.DB
import play.api.db.slick.Config.driver.simple._
import scala.slick.jdbc.{ GetResult, StaticQuery => Q }

import securesocial.core._

import play.api._

import play.api.Play.current
import securesocial.core.UserId
import securesocial.core.OAuth2Info

/*
case class MyIdentity(
  pid: Option[Long] = None,
  userId: String,
  providerId: String,
  email: Option[String],
  firstName: String,
  lastName: String,
  fullName: String,
  avatarUrl: Option[String],
  authMethod: AuthenticationMethod,
  oAuth1Info: Option[OAuth1Info] = None,
  oAuth2Info: Option[OAuth2Info] = None,
  passwordInfo: Option[PasswordInfo] = None,
  myField: Option[String] = None) extends Identity {

  def id: UserId = UserId(userId, providerId)

  def gravatar: Option[String] = email.map {
    e => s"http://www.gravatar.com/avatar/${Codecs.md5(e.getBytes)}.png"
  }
}

object MyIdentity {
  def fromIdentity(user: Identity) = {
    MyIdentity(
      pid = None,
      userId = user.id.id,
      providerId = user.id.providerId,
      email = user.email,
      firstName = user.firstName,
      lastName = user.lastName,
      fullName = user.fullName,
      avatarUrl = user.avatarUrl,
      authMethod = user.authMethod,
      oAuth1Info = user.oAuth1Info,
      oAuth2Info = user.oAuth2Info,
      passwordInfo = user.passwordInfo)
  }

  def fromUser(user: User) = {
    MyIdentity(
      pid = None,
      userId = user.userId,
      providerId = user.providerId,
      email = user.email,
      firstName = user.firstName,
      lastName = user.lastName,
      fullName = user.firstName + " " + user.lastName,
      avatarUrl = user.avatarUrl,
      authMethod = user.authMethod,
      oAuth1Info = None,
      oAuth2Info = None,
      passwordInfo = None)
  }
}
*/ 

case class User (
  uid: Option[Long] = None,
  userId: String,
  providerId: String,
  email: Option[String],
  firstName: String,
  lastName: String,
  authMethod: AuthenticationMethod,
  avatarUrl: Option[String],
  accessToken: String,
  tokenType: Option[String],
  tokenExpiresIn: Option[Int],
  refreshToken: Option[String],
  admin: Boolean = false,
  canOpen: Boolean = false
  ) extends Identity {
  
  def id: UserId = UserId(userId, providerId)
  def fullName: String = s"$firstName $lastName"
  def gravatar: Option[String] = email.map {
    e => s"http://www.gravatar.com/avatar/${Codecs.md5(e.getBytes)}.png"
  }
  val oAuth1Info = None
  val oAuth2Info: Option[OAuth2Info] = Some(OAuth2Info(accessToken, tokenType, tokenExpiresIn, refreshToken))
  val passwordInfo = None
}

object User {
  def fromIdentity(identity: Identity) = {
    User(
      uid = None,
      userId = identity.id.id,
      providerId = identity.id.providerId,
      email = identity.email,
      firstName = if (!identity.firstName.isEmpty) identity.firstName else identity.fullName.split(' ').head,
      lastName = if (!identity.lastName.isEmpty) identity.lastName else identity.fullName.split(' ').tail.head,
      authMethod = identity.authMethod,
      avatarUrl = identity.avatarUrl,
      accessToken = identity.oAuth2Info.get.accessToken,
      tokenType = identity.oAuth2Info.get.tokenType,
      tokenExpiresIn = identity.oAuth2Info.get.expiresIn,
      refreshToken = identity.oAuth2Info.get.refreshToken
      )
  }
  
 // def applyFlat(uid, userId, providerId, email, firstName lastName, authMethod, avatarUrl, accessToken, tokenType, expiresIn, refreshToken)
}

object Users extends Table[User]("users") with StandardTable {
  // Conversions for AuthenticationMethod
  implicit def string2AuthenticationMethod: TypeMapper[AuthenticationMethod] = MappedTypeMapper.base[AuthenticationMethod, String](
    authenticationMethod => authenticationMethod.method,
    string => AuthenticationMethod(string))

  //  val userSequence = Sequence[Int]("user_seq") start 1 inc 1 <-- This is created manually in an eveolution for now
  def uid = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def userId = column[String]("userId")
  def providerId = column[String]("providerId")
  def email = column[Option[String]]("email")
  def firstName = column[String]("firstName")
  def lastName = column[String]("lastName")
  def authMethod = column[AuthenticationMethod]("authMethod")
  def avatarUrl = column[Option[String]]("avatarUrl")

  //  oAuth2Info
  def accessToken = column[String]("accessToken")
  def tokenType = column[Option[String]]("tokenType")
  def expiresIn = column[Option[Int]]("expiresIn")
  def refreshToken = column[Option[String]]("refreshToken")
  
  /* Custom fields */
  def admin = column[Boolean]("admin")
  def canOpen = column[Boolean]("canOpen")

  // Projections
  def * = uid.? ~ userId ~ providerId ~ email ~ firstName ~ lastName ~ authMethod ~ avatarUrl ~ accessToken ~ tokenType ~ expiresIn ~ refreshToken ~ admin ~ canOpen <> (User.apply _, User.unapply _)
  def autoInc = * returning uid

  // Operations
  def save(user: User, identityOnly: Boolean = false): User = DB.withTransaction {
    implicit session =>
      findByUserId(user.id) match {
        case None => {
          val uid = this.autoInc.insert(user.copy(uid = getNextId("users_id")))
          user.copy(uid = Some(uid))
        }
        case Some(u) => {
          val fullUser =
            if (identityOnly) u.copy(userId = user.userId, providerId = user.providerId, email = user.email, firstName = user.firstName, lastName = user.lastName, authMethod = user.authMethod, avatarUrl = user.avatarUrl)
            else user
          if (u != fullUser) Query(Users).where(_.uid is u.uid).update(fullUser) // user.copy(uid = u.uid, myField = u.myField)
          fullUser
        }
      }
  }

  def delete(uid: Long) = DB.withTransaction {
    implicit session =>
      this.where(_.uid is uid).mutate(_.delete)
  }

  // Queries
  def all: List[User] = DB.withSession {
    implicit session =>
      val q = for (user <- Users) yield user
      q.list
  }

  def findById(uid: Long): Option[User] = DB.withSession {
    implicit session =>
      def byId = createFinderBy(_.uid)
      byId(uid).firstOption
  }

  //  def findByEmail(email: String): Option[User] = DB.withSession {
  //    implicit session =>
  //      def byEmail = createFinderBy(_.email)
  //      byEmail(email).firstOption
  //  }

  def findByUserId(userId: UserId): Option[User] = DB.withSession {
    implicit session =>
      val q = for {
        user <- Users
        if (user.userId is userId.id) && (user.providerId is userId.providerId)
      } yield user

      q.firstOption
  }

  def findByEmailAndProvider(email: String, providerId: String): Option[User] = DB.withSession {
    implicit session =>
      val q = for {
        user <- Users
        if (user.email is email) && (user.providerId is providerId)
      } yield user

      q.firstOption
  }
}
