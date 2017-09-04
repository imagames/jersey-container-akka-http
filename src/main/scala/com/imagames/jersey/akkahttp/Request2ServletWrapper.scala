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

import java.net.HttpCookie
import javax.servlet.http.{Cookie, HttpServletRequest, HttpServletResponse, HttpUpgradeHandler}
import javax.servlet.{DispatcherType, ServletRequest, ServletResponse}

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.Uri.Query

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

/*
* This class provides support for injecting HttpServletRequest on jersey services.
* This implementation is only useful to access request headers, otherwise it's almost useless.
* Please use Jersey APIs instead and stop using old Servlet APIs. Make a better world.
*/
class Request2ServletWrapper(val req: HttpRequest) extends HttpServletRequest {

    lazy val parsedQuery = req.uri.queryString().map(s => Query(s).toMultiMap)

    override def getPathInfo = req.getUri().path()

    override def getUserPrincipal = null

    override def getServletPath = this.getContextPath

    override def getDateHeader(name: String): Long = {
        val h = req.getHeader("Date")
        if (h.isPresent) {
            return Try(h.get().value().toLong) match {
                case Success(ll) => ll
                case _ => -1
            }
        } else {
            return -1
        }
    }

    override def getIntHeader(name: String) = {
        val h = req.getHeader("Date")
        if (h.isPresent) {
            Try(h.get().value().toInt) match {
                case Success(ll) => ll
                case _ => -1
            }
        } else {
            -1
        }
    }

    override def getMethod = req.method.value

    override def getContextPath = req.uri.path.toString()

    override def isRequestedSessionIdFromUrl = false

    override def getPathTranslated = getPathInfo()

    override def getRequestedSessionId = null

    override def isRequestedSessionIdFromURL = false

    override def logout() = {}

    override def changeSessionId() = null

    override def getRequestURL = new StringBuffer(req.uri.path.toString())

    override def upgrade[T <: HttpUpgradeHandler](handlerClass: Class[T]): T = null.asInstanceOf[T]

    override def getRequestURI = req.uri.path.toString()

    override def isRequestedSessionIdValid = false

    override def getAuthType = null

    override def authenticate(response: HttpServletResponse) = false

    override def login(username: String, password: String) = {}

    override def getHeader(name: String) = req.headers.find(_.name() == name).map(_.value()) orNull

    override def getCookies: Array[Cookie] = req.headers.find(_.name() == "Cookie").map(_.value()).map(cstr => {
        val cks = HttpCookie.parse(cstr)
        cks.asScala.map(hc => {
            val c = new Cookie(hc.getName(), hc.getValue())
            c.setComment(hc.getComment())
            c.setDomain(hc.getDomain())
            c.setHttpOnly(hc.isHttpOnly())
            c.setMaxAge(hc.getMaxAge().toInt)
            c.setPath(hc.getPath())
            c.setSecure(hc.getSecure())
            c.setVersion(hc.getVersion())
            c
        }).toArray
    }) orNull

    override def getParts = null

    override def getHeaders(name: String) = {
        req.headers.find(_.name() == name).map(h => {
            val it = List(h.value()).iterator
            new java.util.Enumeration[String]() {
                override def hasMoreElements = it.hasNext

                override def nextElement() = it.next()
            }
        }) orNull
    }

    override def getQueryString = req.uri.queryString() orNull

    override def getPart(name: String) = null

    override def isUserInRole(role: String) = false

    override def getRemoteUser = null

    override def getHeaderNames = {
        val it = req.headers.map(_.name()).iterator
        new java.util.Enumeration[String]() {
            override def hasMoreElements = it.hasNext

            override def nextElement() = it.next()
        }
    }

    override def isRequestedSessionIdFromCookie = false

    override def getSession(create: Boolean) = null

    override def getSession = null

    override def getRemoteAddr: String = {
        val test1 = this.getHeader("X-Forwarded-For")
        if (test1 != null) {
            return test1
        }
        val test2 = this.getHeader("Remote-Address")
        if (test2 != null) {
            return test2
        }
        val test3 = this.getHeader("X-Real-IP")
        if (test3 != null) {
            return test3
        }
        return null
    }

    override def getParameterMap = {
        parsedQuery.map(_.mapValues(_.toArray).asJava) orNull
    }

    override def getServerName = null

    override def getRemotePort = 0

    override def getParameter(name: String) = parsedQuery.map(_.find(e => e._1 == name && e._2.size > 0).map(_._2(0)) orNull) orNull

    override def getRequestDispatcher(path: String) = throw new UnsupportedOperationException()

    override def getAsyncContext = null

    override def isAsyncSupported = false

    override def getContentLength: Int = {
        val h = req.getHeader("Content-Length")
        if (h.isPresent) {
            val l = Try(h.get().value().toInt)
            l.getOrElse(-1)
        } else {
            -1
        }
    }

    override def getInputStream = throw new UnsupportedOperationException()

    override def isAsyncStarted = false

    override def startAsync() = throw new UnsupportedOperationException()

    override def startAsync(servletRequest: ServletRequest, servletResponse: ServletResponse) = throw new UnsupportedOperationException()

    override def setCharacterEncoding(env: String) = throw new UnsupportedOperationException()

    override def getCharacterEncoding = {
        val a = req.entity.getContentType().getCharsetOption
        if (a.isPresent) {
            a.toString
        } else {
            null
        }
    }

    override def getServerPort = -1

    override def getAttributeNames = new java.util.Enumeration[String]() {
        override def hasMoreElements = false

        override def nextElement() = null
    }

    override def getContentLengthLong = {
        val h = req.getHeader("Content-Length")
        if (h.isPresent) {
            val l = Try(h.get().value().toLong)
            l.getOrElse(-1)
        } else {
            -1
        }
    }

    override def getParameterNames = {
        parsedQuery.map(p => {
            val it = p.keySet.iterator
            new java.util.Enumeration[String]() {
                override def hasMoreElements = it.hasNext
                override def nextElement() = it.next()
            }
        }) orNull
    }

    override def getContentType = req.entity.contentType.toString()

    override def getLocalPort = -1

    override def getServletContext = null

    override def getRemoteHost = null

    override def getLocalAddr = null

    override def getRealPath(path: String) = req.uri.path.toString()

    override def setAttribute(name: String, o: scala.Any) = {}

    override def getAttribute(name: String) = null

    override def getLocales = null

    override def removeAttribute(name: String) = {}

    override def getParameterValues(name: String) = {
        parsedQuery.map(_.find(_._1 == name).map(_._2.toArray) orNull) orNull
    }

    override def getScheme = req.uri.scheme

    override def getReader = throw new UnsupportedOperationException()

    override def isSecure = req.uri.scheme.toLowerCase == "https"

    override def getProtocol = req.uri.scheme

    override def getLocalName = null

    override def getDispatcherType = DispatcherType.REQUEST

    override def getLocale = null
}
