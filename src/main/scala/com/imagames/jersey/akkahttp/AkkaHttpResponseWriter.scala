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

import java.io.{ByteArrayOutputStream, OutputStream, PipedInputStream, PipedOutputStream}
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import org.glassfish.jersey.server.ContainerResponse
import org.glassfish.jersey.server.spi.ContainerResponseWriter

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise, blocking}
import scala.util.Try

class AkkaHttpResponseWriter(request: HttpRequest, callback: Promise[HttpResponse], enableChunkedResponse: Boolean = false)(implicit actorSystem: ActorSystem, ec: ExecutionContext, am: ActorMaterializer) extends ContainerResponseWriter {

    private var cachedHeaders: List[HttpHeader] = List()
    private var cachedBAOS: ByteArrayOutputStream = null
    private var cachedSrc: Source[ByteString, _] = null
    private var cachedStatus = -1
    private var _isSuspended = false
    private var timeout: Option[Cancellable] = None
    private var futtt = Future.successful()

    override def suspend(timeOut: Long, timeUnit: TimeUnit, timeoutHandler: ContainerResponseWriter.TimeoutHandler): Boolean = {
        // Set timeout
        setTimeout(timeOut, timeUnit)
        this._isSuspended = true
        this._isSuspended
    }

    private def getCachedEntity() = if (this.cachedBAOS != null && this.cachedBAOS.size() > 0 && StatusCode.int2StatusCode(this.cachedStatus).allowsEntity()) {
        val ct = cachedHeaders.find(_.name() == "Content-Type")
        ct map { c =>
            ContentType.parse(c.value()) match {
                case Right(contentType) => HttpEntity(contentType, cachedBAOS.toByteArray)
                case _ => HttpEntity(cachedBAOS.toByteArray)
            }
        } getOrElse HttpEntity(cachedBAOS.toByteArray)
    } else HttpEntity.Empty

    private def getChunkedEntity() = if (this.cachedSrc != null && StatusCode.int2StatusCode(this.cachedStatus).allowsEntity()) {
        val src = this.cachedSrc
        val ct = cachedHeaders.find(_.name() == "Content-Type")
        ct map { c =>
            ContentType.parse(c.value()) match {
                case Right(contentType) => HttpEntity(contentType, src)
                case _ => HttpEntity(ContentTypes.`application/octet-stream`, src)
            }
        } getOrElse HttpEntity(ContentTypes.`application/octet-stream`, src)
    } else HttpEntity.Empty

    override def commit() = {
        try {
            val ent = if (enableChunkedResponse) getChunkedEntity() else getCachedEntity()

            val resp = HttpResponse(StatusCode.int2StatusCode(this.cachedStatus), cachedHeaders.filterNot(_.name() == "Content-Type"), ent, HttpProtocols.`HTTP/1.1`)
            this.timeout.map(t => Try(t.cancel()))
            callback.trySuccess(resp)

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

        this.cachedStatus = responseContext.getStatus

        if (contentLength == 0) {
            null
        } else if (enableChunkedResponse) {

            val in = new PipedInputStream
            val out = new PipedOutputStream(in)
            val src = StreamConverters.fromInputStream(() => in)

            this.cachedSrc = src

            futtt = Future.successful()

            new OutputStream {

                override def flush(): Unit = {
                    futtt = futtt.flatMap(_ => Future {
                        blocking {
                            out.flush()
                        }
                    })
                }

                override def close() = {
                    futtt = futtt.flatMap(_ => Future {
                        blocking {
                            out.close()
                        }
                    })
                }

                override def write(b: Array[Byte]) = {
                    futtt = futtt.flatMap(_ => Future {
                        blocking {
                            out.write(b)
                        }
                    })
                }

                override def write(b: Array[Byte], off: Int, len: Int) = {
                    futtt = futtt.flatMap(_ => Future {
                        blocking {
                            out.write(b, off, len)
                        }
                    })
                }

                override def write(b: Int): Unit = {
                    futtt = futtt.flatMap(_ => Future {
                        blocking {
                            out.write(b)
                        }
                    })
                }
            }
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
