/*
* Copyright 2017 Imagames Gamification Services S.L.
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
*/
package com.imagames.jersey.akkahttp

import java.lang.reflect.Type
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.ws.rs.core.{Application, GenericType, SecurityContext}

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.extractRequestContext
import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import org.glassfish.hk2.api.ServiceLocator
import org.glassfish.jersey.internal.MapPropertiesDelegate
import org.glassfish.jersey.internal.util.collection.Ref
import org.glassfish.jersey.server.spi.RequestScopedInitializer
import org.glassfish.jersey.server.{ApplicationHandler, ContainerRequest, ResourceConfig}

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try
import scala.concurrent.blocking

/*
* This is the class to use to convert Jersey application to Akka Http Route or to (HttpRequest => Future[HttpResponse]) function
* Can be converted to Route using implicit conversion
* Usage:
* val jerseyRoute = Jersey2AkkaHttpContainer(jerseyApp)
* val routes = {
*     pathPrefix("api") {
*         jerseyRoute
*     }
* }
*/

object Jersey2AkkaHttpContainer {
    def apply(application: Application, enableChunkedResponse: Boolean = false) = new Jersey2AkkaHttpContainer(application, enableChunkedResponse)

    implicit def toRoute(from: Jersey2AkkaHttpContainer)(implicit as: ActorSystem, ec: ExecutionContext, am: ActorMaterializer): Route = from.directive()
}

class Jersey2AkkaHttpContainer(application: Application, enableChunkedResponse: Boolean = false) extends org.glassfish.jersey.server.spi.Container {

    private var appHandler: Option[ApplicationHandler] = None

    this.appHandler = Some(new ApplicationHandler(application, new AkkaHttpBinder()))

    override def getApplicationHandler = this.appHandler getOrElse null

    override def getConfiguration = this.appHandler.map(_.getConfiguration()) getOrElse null

    override def reload() = reload(getConfiguration())

    override def reload(configuration: ResourceConfig) = {
        this.appHandler.map {
            _.onShutdown(this)
        }

        this.appHandler = Some(new ApplicationHandler(ConfigurationRegisterAdapter.register(configuration, new AkkaHttpBinder())))
        this.appHandler.map { h =>
            h.onReload(this)
            h.onStartup(this)
        }
    }

    def handler(req: HttpRequest)(implicit as: ActorSystem, ec: ExecutionContext, am: ActorMaterializer): Future[HttpResponse] = myhandler(req, req.uri.path)

    def route(rc: RequestContext)(implicit as: ActorSystem, ec: ExecutionContext, am: ActorMaterializer): Route = rc => {
        myhandler(rc.request, rc.unmatchedPath).map {
            RouteResult.Complete(_)
        }
    }

    def directive()(implicit as: ActorSystem, ec: ExecutionContext, am: ActorMaterializer): Route = extractRequestContext {
        c => this.route(c)
    }

    private def getBaseUri(uri: Uri, path: Uri.Path) = {
        val u = uri.toString()
        val p = path.toString()
        val i = u.indexOf(p)
        if (i >= 0) u.substring(0, i) else u
    }

    private def getHostUri(uri: Uri) = getBaseUri(uri, uri.path).toString

    private def getSecurityContext(request: HttpRequest) = new SecurityContext() {
        override def isUserInRole(role: String) = false

        override def isSecure = false

        override def getUserPrincipal = null

        override def getAuthenticationScheme = null
    }

    private def myhandler(request: HttpRequest, unmatchedUri: Uri.Path)(implicit as: ActorSystem, ec: ExecutionContext, am: ActorMaterializer): Future[HttpResponse] = {
        val baseUri = getHostUri(request.uri) + "/"
        val requestUri = request.uri.rawQueryString match {
            case Some(qs) => unmatchedUri.toString() + "?" + qs
            case _ => unmatchedUri.toString()
        }

        // Basic
        val contReq = new ContainerRequest(URI.create(baseUri), URI.create(requestUri),
            request.method.value, getSecurityContext(request), new MapPropertiesDelegate())

        // Headers
        request.getHeaders().asScala.foreach(h => contReq.header(h.name(), h.value()))

        // Input body
        val inputStream = request.entity.dataBytes
                .runWith(
                    StreamConverters.asInputStream(FiniteDuration(7, TimeUnit.SECONDS))
                )
        contReq.setEntityStream(inputStream)
        // Content-Type header
        Try {
            val ct = request.entity.getContentType().mediaType.toString
            if (ct != null && ct.length > 0 && ct != "none/none") {
                contReq.header("Content-Type", ct)
            }
        }

        // Inject resolver
        val RequestTYPE: Type = (new GenericType[Ref[HttpRequest]]() {}).getType
        contReq.setRequestScopedInitializer(new RequestScopedInitializer {
            override def initialize(locator: ServiceLocator): Unit = {
                locator.getService[Ref[HttpRequest]](RequestTYPE).set(request)
            }
        })

        // Writer
        val p = Promise[HttpResponse]()
        contReq.setWriter(new AkkaHttpResponseWriter(request, p, enableChunkedResponse = this.enableChunkedResponse))

        for {
            _ <- Future { blocking { this.appHandler.get.handle(contReq) } }
            f <- p.future
        } yield f
    }
}
