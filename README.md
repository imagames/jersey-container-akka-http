# jersey-container-akka-http
Jersey 2 container to deploy Jersey 2 applications over Akka-Http server. Provides low level and high level APIs.

Packages built for Scala 2.11 and Scala 2.12
Using: 
 * akka-http 10.0.10
 * akka-actor 2.4.19
 * akka-stream 2.4.19
 * jersey-common 2.25.1
 * jersey-server 2.25.1

## Usage
```scala
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

##Using pre-built binaries

###SBT
```
resolvers += "Artifactory" at "https://repo.imagames.com/public-local/"
```

###Gradle
```
repositories {
    maven { url "https://repo.imagames.com/public-local/" }
}
```
