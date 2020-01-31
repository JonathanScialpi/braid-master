/**
 * Copyright 2018 Royal Bank of Scotland
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
package io.bluebank.braid.core.http

import io.bluebank.braid.core.logging.loggerFor
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpHeaderValues.*
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpHeaders.*
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer

fun Router.setupAllowAnyCORS() {
  route().handler {
    // allow all origins .. TODO: set this up with configuration
    val origin = it.request().getHeader("Origin")
    if (origin != null) {
      it.response().putHeader("Access-Control-Allow-Origin", origin)
      it.response().putHeader("Access-Control-Allow-Credentials", "true")
      it.response().putHeader(
        "Access-Control-Allow-Headers",
        "Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With"
      )
    }
    it.next()
  }
}

fun Router.setupOptionsMethod() {
  options().handler {
    it.response()
      .putHeader(
        ALLOW,
        "GET, PUT, POST, OPTIONS, CONNECT, HEAD, DELETE, CONNECT, TRACE, PATCH"
      )
      .putHeader(CONTENT_TYPE, "text/*")
      .putHeader(CONTENT_TYPE, "application/*")
      .end()
  }
}

var log = loggerFor<Router>()

fun HttpServerResponse.end(
  error: Throwable,
  statusCode: Int = 500,
  statusMessage: String? = null
) {
  val e = if (error is InvocationTargetException) error.targetException else error
  log.trace("returning error to client", e)
  val payload = Json.encode(e)
  val message = statusMessage ?: e.message ?: "Undefined error"
  this
    .putHeader(CONTENT_TYPE, APPLICATION_JSON)
    .putHeader(CONTENT_LENGTH, payload.length.toString())
    .setStatusMessage(message.replace("\n", "").replace("\r", ""))
    .setStatusCode(statusCode)
    .end(payload)
}

fun <T> HttpServerResponse.end(future: Future<T>) {
  future.setHandler {
    if (it.succeeded()) {
      this.end(it.result())
    } else {
      val cause = it.cause()
      when (cause) {
        is io.vertx.ext.web.handler.impl.HttpStatusException -> this.end(
          cause,
          cause.statusCode,
          cause.message
        )
        is javax.xml.ws.http.HTTPException -> this.end(
          cause,
          cause.statusCode,
          cause.message
        )
        else -> this.end(it.cause())
      }
    }
  }
}

fun <T> HttpServerResponse.end(value: T) {
  when (value) {
    is Throwable -> this.end(value)
    is Future<*> -> this.end(value)
    is String -> this.endWithString(value)
    is Buffer -> this.endWithBuffer(value)
    is ByteArray -> this.endWithByteArray(value)
    is ByteBuffer -> this.endWithByteBuffer(value)
    is ByteBuf -> this.endWithByteBuf(value)
    is JsonArray -> this.end(value)
    is JsonObject -> this.end(value)
    else -> {
      val payload = Json.encode(value)
      this
        .putHeader(CONTENT_LENGTH, payload.length.toString())
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .end(payload)
    }
  }
}

fun HttpServerResponse.endWithString(value: String) {
  this.putHeader(CONTENT_LENGTH, value.length.toString())
    .putHeader(CONTENT_TYPE, TEXT_PLAIN)
    .end(value)
}

fun HttpServerResponse.endWithBuffer(value: Buffer) {
  this.putHeader(CONTENT_LENGTH, value.length().toString())
    .putHeader(CONTENT_TYPE, APPLICATION_OCTET_STREAM)
    .end(value)
}

fun HttpServerResponse.endWithByteBuffer(value: ByteBuffer) {
  endWithByteArray(value.array())
}

fun HttpServerResponse.endWithByteArray(value: ByteArray) {
  endWithBuffer(Buffer.buffer(value))
}

fun HttpServerResponse.endWithByteBuf(value: ByteBuf) {
  endWithBuffer(Buffer.buffer(value))
}

val HttpClientResponse.failed: Boolean
  get() {
    return (statusCode() / 100) != 2
  }

fun HttpServerResponse.end(value: JsonArray) {
  val payload = value.encode()
  this
    .putHeader(CONTENT_LENGTH, payload.length.toString())
    .putHeader(CONTENT_TYPE, "application/json")
    .end(payload)
}

fun HttpServerResponse.end(value: JsonObject) {
  val payload = value.encode()
  this
    .putHeader(CONTENT_LENGTH, payload.length.toString())
    .putHeader(CONTENT_TYPE, "application/json")
    .end(payload)
}

fun RoutingContext.end(text: String) {
  val length = text.length
  response().apply {
    putHeader(HttpHeaders.CONTENT_LENGTH, length.toString())
    putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
    end(text)
  }
}

fun <T : Any> RoutingContext.end(obj: T) {
  response().end(obj)
}

fun RoutingContext.end(
  err: Throwable,
  statusCode: Int = 500,
  statusMessage: String? = null
) {
  response().end(err, statusCode, statusMessage)
}

