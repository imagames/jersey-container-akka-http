# jersey-container-akka-http
Jersey 2 container to deploy Jersey 2 applications over Akka-Http server. Provides low level and high level APIs.

Packages built for Scala 2.12 and Scala 2.13

Using: 
 * akka-http 10.1.11
 * akka-actor 2.6.9
 * akka-stream 2.6.9
 * jersey-common 2.25.1
 * jersey-server 2.25.1

## Usage
```scala
// Define/import implicit arguments
implicit val system = ActorSystem()
implicit val executor = system.dispatcher
implicit val materializer = ActorMaterializer()

val jerseyApp = new ResourceConfig
...
val jerseyRoute = Jersey2AkkaHttpContainer(jerseyApp)
val routes = {
    pathPrefix("api") {
        jerseyRoute
    } ~
    pathPrefix("newapi") {
	...
    }
}
```

## Using pre-built binaries

#### SBT
```sbt
resolvers += "Imagames Repo" at "https://repo.imagames.com/public-local/"
libraryDependencies += "com.imagames" %% "jersey-container-akka-http" % "1.2.0"
```

#### Gradle
```gradle
repositories {
    maven { url "https://repo.imagames.com/public-local/" }
}
dependencies {
    compile "com.imagames:jersey-container-akka-http_2.11:1.2.0" // Scala 2.11
    compile "com.imagames:jersey-container-akka-http_2.12:1.2.0" // Scala 2.12
    compile "com.imagames:jersey-container-akka-http_2.13:1.2.0" // Scala 2.13
}
```
