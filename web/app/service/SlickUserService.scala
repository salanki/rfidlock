/**
 * Copyright 2012 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Slick Service by Leon Radley - github.com/leon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package service

import _root_.models._
import play.api._
import securesocial.core._
import securesocial.core.providers.Token
import securesocial.core.UserId
import models._
import securesocial.core.UserId
import securesocial.core.providers.Token

class SlickUserService(application: Application) extends UserServicePlugin(application) {

  /* These finds will be executed on page load, find methods on the User classes should really read from a cache */
  def find(id: UserId): Option[Identity] = Users.findByUserId(id)
  
  def findByEmailAndProvider(email: String, providerId: String): Option[Identity] = Users.findByEmailAndProvider(email, providerId)

  def save(identity: Identity): Identity = {
    val u = User.fromIdentity(identity)
    //Logger.info("save identity to user %s".format(u))
    /* By passing identity = true here we will be returned a complete user object with information fetched from the DB as well */
    Users.save(u, true)
  }

  /* Tokens are only needed for UserPass authentication which we don't use */
  def save(token: Token) {}
  def findToken(token: String): Option[Token] = None 
  def deleteToken(uuid: String) {}
  def deleteTokens() {}
  def deleteExpiredTokens() {}
}
