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

import java.io.{ByteArrayOutputStream, OutputStream}
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, Cancellable, Props, ReceiveTimeout}
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import org.glassfish.jersey.server.ContainerResponse
import org.glassfish.jersey.server.spi.ContainerResponseWriter

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Promise, blocking}
import scala.util.Try
import scala.language.postfixOps

class AkkaHttpResponseWriter(request: HttpRequest, callback: Promise[HttpResponse], enableChunkedResponse: Boolean = false)(implicit actorSystem: ActorSystem, ec: ExecutionContext, am: ActorMaterializer) extends ContainerResponseWriter {

    private var responseCharset: Option[String] = None
    private var cachedHeaders: List[HttpHeader] = List()
    private var cachedAsyncOS: OutputStream = null
    private var cachedBAOS: ByteArrayOutputStream = null
    private var cachedSrc: Source[ByteString, _] = null
    private var cachedStatus = -1
    private var _isSuspended = false
    private var timeout: Option[Cancellable] = None

    override def suspend(timeOut: Long, timeUnit: TimeUnit, timeoutHandler: ContainerResponseWriter.TimeoutHandler): Boolean = {
        // Set timeout
        setTimeout(timeOut, timeUnit)
        this._isSuspended = true
        this._isSuspended
    }

    private def getCachedEntity() = if (this.cachedBAOS != null && this.cachedBAOS.size() > 0 && StatusCode.int2StatusCode(this.cachedStatus).allowsEntity()) {
        HttpEntity(getContentType().getOrElse(ContentTypes.`application/octet-stream`), cachedBAOS.toByteArray)
    } else HttpEntity.Empty

    private def getChunkedEntity() = if (this.cachedSrc != null && StatusCode.int2StatusCode(this.cachedStatus).allowsEntity()) {
        HttpEntity(getContentType().getOrElse(ContentTypes.`application/octet-stream`), this.cachedSrc)
    } else HttpEntity.Empty

    private def getContentType(): Option[ContentType] = {
        val ct = cachedHeaders.find(_.name() == "Content-Type")

        val charset = this.responseCharset.map {
            c => (c, HttpCharset.custom(c))
        }

        ct flatMap { c =>
            MediaType.parse(c.value()) match {
                case Right(mediatype) => charset match {
                    case Some(cs) => Some(ContentType(mediatype.withParams(Map("charset" -> cs._1)), () => cs._2))
                    case _ => Some(ContentType(mediatype, () => HttpCharsets.`UTF-8`))
                }
                case _ => None
            }
        }
    }

    override def commit() = {
        try {
            if (this.enableChunkedResponse) {
                Try(this.cachedAsyncOS.close())
                if (!callback.isCompleted) {
                    // contentLength was 0 at writeResponseStatusAndHeaders
                    val resp = HttpResponse(StatusCode.int2StatusCode(this.cachedStatus), cachedHeaders.filterNot(_.name() == "Content-Type"), HttpEntity.Empty, HttpProtocols.`HTTP/1.1`)
                    callback.trySuccess(resp)
                }
            } else {
                val resp = HttpResponse(StatusCode.int2StatusCode(this.cachedStatus), cachedHeaders.filterNot(_.name() == "Content-Type"), getCachedEntity(), HttpProtocols.`HTTP/1.1`)
                this.timeout.map(t => Try(t.cancel()))
                callback.trySuccess(resp)
            }
        } catch {
            case e: Throwable => callback.tryFailure(e)
        }
    }

    private def setTimeout(timeOut: Long, timeUnit: TimeUnit): Unit = {
        if (timeOut > 0) {
            val to = FiniteDuration(timeOut, timeUnit)
            this.timeout = Some(actorSystem.scheduler.scheduleOnce(to, new Runnable {
                override def run(): Unit = {
                    callback.trySuccess(HttpResponse(StatusCodes.InternalServerError, Nil, HttpEntity("Timeout"), HttpProtocols.`HTTP/1.1`))
                }
            }))
        }
    }

    override def setSuspendTimeout(timeOut: Long, timeUnit: TimeUnit) = {
        this.timeout.map(t => Try(t.cancel()))
        setTimeout(timeOut, timeUnit)
    }

    override def writeResponseStatusAndHeaders(contentLength: Long, responseContext: ContainerResponse): OutputStream = {
        this.cachedHeaders = responseContext.getStringHeaders.entrySet().asScala.filter(h => {
            h.getValue.size() > 0
        }).map(h => {
            HttpHeader.parse(h.getKey, h.getValue.get(0))
        }).filter(_.isInstanceOf[ParsingResult.Ok]).map(_.asInstanceOf[ParsingResult.Ok].header).toList

        this.responseCharset = responseContext.getStringHeaders.asScala.find(_._1.toLowerCase == "content-type").flatMap(h => {
            if (h._2.size() > 0) {
                val v = h._2.get(0).toLowerCase
                val f = v.indexOf("charset=")
                if (f > 0) {
                    Some(v.substring(f + 8))
                } else {
                    None
                }
            } else {
                None
            }
        })

        this.cachedStatus = responseContext.getStatus

        if (contentLength == 0) {
            null
        } else if (enableChunkedResponse) {

            val (out, pub) = StreamConverters.asOutputStream().preMaterialize()
            this.cachedSrc = pub

            val act = actorSystem.actorOf(Props(classOf[AsyncOutputStreamActor], out))

            this.cachedAsyncOS = new OutputStream {

                override def flush(): Unit = {
                    act ! AsyncOutputStreamActor.Flush
                }

                override def close() = {
                    act ! AsyncOutputStreamActor.Close
                }

                override def write(b: Array[Byte]) = {
                    act ! AsyncOutputStreamActor.Write(b.clone())
                }

                override def write(b: Array[Byte], off: Int, len: Int) = {
                    act ! AsyncOutputStreamActor.WriteSub(b.clone, off, len)
                }

                override def write(b: Int): Unit = {
                    act ! AsyncOutputStreamActor.WriteInt(b)
                }

            }
            val resp = HttpResponse(StatusCode.int2StatusCode(this.cachedStatus), cachedHeaders.filterNot(_.name() == "Content-Type"), getChunkedEntity(), HttpProtocols.`HTTP/1.1`)
            callback.trySuccess(resp)
            this.cachedAsyncOS
        } else {
            this.cachedBAOS = if (contentLength > 0) new ByteArrayOutputStream(contentLength.toInt) else new ByteArrayOutputStream()
            this.cachedBAOS
        }
    }

    override def failure(error: Throwable) = {
        callback.tryFailure(error)
    }

    override def enableResponseBuffering() = false

    def isSuspended() = this._isSuspended
}

object AsyncOutputStreamActor {
    sealed trait OutputStreamCommand
    case class Write(array: Array[Byte]) extends OutputStreamCommand
    case class WriteSub(array: Array[Byte], off: Int, len: Int) extends OutputStreamCommand
    case class WriteInt(data: Int) extends OutputStreamCommand
    case object Close extends OutputStreamCommand
    case object Flush extends OutputStreamCommand
}

class AsyncOutputStreamActor(outputStream: OutputStream) extends Actor {
    import AsyncOutputStreamActor._

    context.setReceiveTimeout(1 minute)

    override def receive: Receive = {
        case Write(data) =>
            blocking {
                outputStream.write(data)
            }
        case WriteSub(data, off, len) =>
            blocking {
                outputStream.write(data, off, len)
            }
        case WriteInt(data) =>
            blocking {
                outputStream.write(data)
            }
        case Flush =>
            blocking {
                outputStream.flush()
            }
        case Close =>
            blocking {
                outputStream.close()
            }
            context stop self
        case ReceiveTimeout =>
            blocking {
                outputStream.close()
            }
            context stop self
    }
}
