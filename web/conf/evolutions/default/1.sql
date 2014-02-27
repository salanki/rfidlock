# --- Created by Slick DDL
# To stop Slick DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table "users" ("id" SERIAL NOT NULL PRIMARY KEY,"userId" VARCHAR(254) NOT NULL,"providerId" VARCHAR(254) NOT NULL,"email" VARCHAR(254),"firstName" VARCHAR(254) NOT NULL,"lastName" VARCHAR(254) NOT NULL,"authMethod" VARCHAR(254) NOT NULL,"avatarUrl" VARCHAR(254),"accessToken" VARCHAR(254) NOT NULL,"tokenType" VARCHAR(254),"expiresIn" INTEGER,"refreshToken" VARCHAR(254),"admin" BOOLEAN NOT NULL,"canOpen" BOOLEAN NOT NULL);

# --- !Downs

drop table "users";

