organization := "com.imagames"
name := "jersey-container-akka-http"
isSnapshot := true
version := "1.0.5"

scalaVersion := "2.12.3"
crossScalaVersions := Seq("2.12.3", "2.11.11")

libraryDependencies += "org.glassfish.jersey.core" % "jersey-common" % "2.25.1"
libraryDependencies += "org.glassfish.jersey.core" % "jersey-server" % "2.25.1"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.19"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.4.19"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.10"

publishTo := Some("Artifactory Realm" at "https://repo.imagames.com/public-local")
