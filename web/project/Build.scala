import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "rfidlock"
    val appVersion      = "1.0"

    val appDependencies = Seq(
    	jdbc, // Remove for slick?
    	anorm, // Remove for slick?
        "com.typesafe.play" %% "play-slick" % "0.3.3",
	"postgresql" % "postgresql" % "9.1-901.jdbc4",
	"securesocial" %% "securesocial" % "master-SNAPSHOT", 

	"com.github.tototoshi" %% "slick-joda-mapper" % "0.3.0"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
        resolvers += Resolver.url("sbt-plugin-snapshots", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/"))(Resolver.ivyStylePatterns)
    )

}
            
