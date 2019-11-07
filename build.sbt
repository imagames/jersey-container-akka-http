organization := "com.imagames"
name := "jersey-container-akka-http"
isSnapshot := true
version := "1.2.0"

scalaVersion := "2.12.3"
crossScalaVersions := Seq("2.12.10", "2.13.1", "2.11.12")

libraryDependencies += "org.glassfish.jersey.core" % "jersey-common" % "2.25.1"
libraryDependencies += "org.glassfish.jersey.core" % "jersey-server" % "2.25.1"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.23"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.23"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.1.8"

publishTo := Some("Artifactory Realm" at "https://repo.imagames.com/public-local")
