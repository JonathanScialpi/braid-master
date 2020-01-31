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
@file:Suppress("DEPRECATION")

package io.bluebank.braid.corda.rest

//import io.swagger.v3.oas.annotations.
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpHeaderValues
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.ext.auth.User
import io.vertx.ext.web.RoutingContext
import net.corda.core.CordaException
import java.nio.ByteBuffer
import javax.ws.rs.HeaderParam
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType

const val X_HEADER_LIST_STRING = "x-list-string"
const val X_HEADER_STRING = "x-string"

class TestService {
  fun sayHello() = "hello"
  fun sayHelloAsync() = Future.succeededFuture("hello, async!")
  fun quietAsyncVoid(): Future<Void> = Future.succeededFuture()
  fun quietAsyncUnit(): Future<Unit> = Future.succeededFuture()
  fun quietUnit(): Unit = Unit
  fun echo(msg: String) = "echo: $msg"
  fun getBuffer(): Buffer = Buffer.buffer("hello")
  fun getByteArray(): ByteArray = Buffer.buffer("hello").bytes
  fun getByteBuf(): ByteBuf = Buffer.buffer("hello").byteBuf
  fun getByteBuffer(): ByteBuffer = Buffer.buffer("hello").byteBuf.nioBuffer()
  fun doubleBuffer(bytes: Buffer): Buffer =
    Buffer.buffer(bytes.length() * 2)
      .appendBytes(bytes.bytes)
      .appendBytes(bytes.bytes)

  fun throwCordaException(): String {
    throw CordaException(
      "something went wrong",
      java.lang.RuntimeException("sub exception")
    )
  }

  @Operation(
    description = "do something custom",
    responses = [ApiResponse(content = arrayOf(Content(mediaType = MediaType.TEXT_PLAIN, schema = Schema(implementation = String::class))))],
    requestBody = RequestBody(content = arrayOf(Content(mediaType = MediaType.TEXT_PLAIN)))
  )
  fun somethingCustom(rc: RoutingContext) {
    val name = rc.request().getParam("foo") ?: "Margaret"
    rc.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
      .setChunked(true).end(Json.encode("Hello, $name!"))
  }

  @Operation(
    description = "return list of strings",
    responses = [ApiResponse(content = arrayOf(Content(mediaType = MediaType.TEXT_PLAIN, array = ArraySchema(schema = Schema(implementation = String::class)))))]
  )
  fun returnsListOfStuff(context: RoutingContext) {
    context.response()
      .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
      .setChunked(true).end(Json.encode(listOf("one", "two")))
  }

  fun willFail(): String {
    throw RuntimeException("on purpose failure")
  }

  fun headerListOfStrings(@HeaderParam(X_HEADER_LIST_STRING) value: List<String>): List<String> {
    return value
  }

  fun headerListOfInt(@HeaderParam(X_HEADER_LIST_STRING) value: List<Int>): List<Int> {
    return value
  }

  fun optionalHeader(@HeaderParam(X_HEADER_STRING) value: String?): String {
    return value ?: "null"
  }

  fun nonOptionalHeader(@HeaderParam(X_HEADER_STRING) value: String): String {
    return value
  }

  fun headers(@Context headers: javax.ws.rs.core.HttpHeaders): List<Int> {
    val acceptableLanguages = headers.acceptableLanguages
    assert(acceptableLanguages.size == 1 && acceptableLanguages.first().language == "*")
    val acceptableMediaTypes = headers.acceptableMediaTypes
    assert(acceptableMediaTypes.size == 1 && acceptableMediaTypes.first() == MediaType.WILDCARD_TYPE)
    val cookies = headers.cookies
    assert(cookies.isEmpty())
    val date = headers.date
    assert(date == null)
    val language = headers.language
    assert(language == null)
    val length = headers.length
    assert(length == -1)
    val mediaType = headers.mediaType
    assert(mediaType == null)

    return headers.getRequestHeader(X_HEADER_LIST_STRING).map { it.toInt() }
  }

  fun mapNumbersToStrings(numbers: Map<Int, Int>): Map<String, String> {
    return numbers.map { it.key.toString() to it.value.toString() }.toMap()
  }

  fun mapMapOfNumbersToMapOfStrings(data: Map<String, List<Int>>): Map<String, List<String>> {
    return data.map { entry ->
      entry.key to entry.value.map { it.toString() }
    }.toMap()
  }

  fun sum(request: SumRequest): SumResponse =
    SumResponse(request.lhs + request.rhs, request.nonce)

  fun whoami(@Context user: User?): String =
    user?.principal()?.toString() ?: "not logged in"
}

data class LoginRequest(
  @Schema(
    description = "user name",
    example = "sa"
  ) val user: String,
  @Schema(
    description = "password",
    example = "admin"
  ) val password: String
)

abstract class AbstractRequest(val nonce: Int)
abstract class AbstractResult(val nonce: Int)
class SumRequest(val lhs: Int, val rhs: Int, nonce: Int) : AbstractRequest(nonce)
class SumResponse(val value: Int, nonce: Int) : AbstractResult(nonce)

