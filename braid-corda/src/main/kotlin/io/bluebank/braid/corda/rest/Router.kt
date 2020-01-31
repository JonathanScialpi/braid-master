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
package io.bluebank.braid.corda.rest

import io.bluebank.braid.corda.rest.Router.Companion.log
import io.bluebank.braid.corda.rest.docs.javaTypeIncludingSynthetics
import io.bluebank.braid.core.http.end
import io.bluebank.braid.core.http.parseQueryParams
import io.bluebank.braid.core.jsonrpc.Converter
import io.bluebank.braid.core.logging.loggerFor
import io.netty.buffer.ByteBuf
import io.swagger.v3.oas.annotations.Parameter
import io.vertx.codegen.annotations.Nullable
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.Json
import io.vertx.ext.auth.User
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import java.net.URLDecoder
import java.nio.ByteBuffer
import javax.ws.rs.*
import javax.ws.rs.core.Context
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.Response
import kotlin.reflect.*
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSuperclassOf

const val HTTP_UNPROCESSABLE_STATUS_CODE = 422

class Router {
  companion object {
    val log = loggerFor<Router>()
  }
}

fun <R> Route.bind(fn: KCallable<R>) {
  fn.validateParameters()
  this.handler { rc ->
    try {
      val args = fn.parseArguments(rc)
      try {
        rc.response().end(fn.call(*args))
      } catch (e: Throwable) {
        log.warn("Unable to call: ${rc.request().path()}", e)
        rc.response().end(e, HTTP_UNPROCESSABLE_STATUS_CODE)
      }
    } catch (e: Throwable) {
      log.warn("Unable to parse parameters: ${rc.request().path()}", e)
      rc.response().end(e, Response.Status.BAD_REQUEST.statusCode)
    }
  }
}

private fun <R> KCallable<R>.parseArguments(context: RoutingContext): Array<Any?> {
  return this.parameters.map { it.parseParameter(context) }.toTypedArray()
}

private fun parseBodyParameter(
  parameter: KParameter,
  context: RoutingContext
): ParseResult {
  try {
    return parseComplexType(parameter, context.body)
  } catch (ex: Throwable) {
    throw RuntimeException(
      "failed to parse body parameter for ${parameter.name}: ${ex.message}",
      ex
    )
  }
}

private fun parseComplexType(parameter: KParameter, body: @Nullable Buffer): ParseResult {
  val type = parameter.getType()
  return when {
    type.isSubclassOf(Buffer::class) -> {
      ParseResult(body)
    }
    type.isSubclassOf(ByteArray::class) -> {
      ParseResult(body.bytes)
    }
    type.isSubclassOf(ByteBuf::class) -> {
      ParseResult(body.byteBuf)
    }
    type.isSubclassOf(ByteBuffer::class) -> {
      ParseResult(ByteBuffer.wrap(body.bytes))
    }
    type == String::class -> {
      ParseResult(body.toString())
    }
    else -> {
      if (body.length() > 0) {
        val constructType =
          Json.mapper.typeFactory.constructType(parameter.type.javaTypeIncludingSynthetics())
        ParseResult(Json.mapper.readValue<Any>(body.toString(), constructType))
      } else {
        ParseResult.NOT_FOUND
      }
    }
  }
}

private fun KParameter.parseSimpleType(paramString: String): Any {
  val k = this.getType()
  return when (k) {
    Int::class -> paramString.toInt()
    Double::class -> paramString.toDouble()
    Float::class -> paramString.toFloat()
    Boolean::class -> paramString.toBoolean()
    Short::class -> paramString.toShort()
    Long::class -> paramString.toLong()
    Byte::class -> paramString.toByte()
    String::class -> paramString
    else -> throw RuntimeException("don't know how to simple-parse $k")
  }
}

private fun KParameter.getType(): KClass<*> {
  return when (this.type.classifier) {
    is KClass<*> -> {
      this.type.classifier as KClass<*>
    }
    else -> throw RuntimeException("parameter doesn't have a class type")
  }
}

private data class ParseResult(val found: Boolean, val result: Any? = null) {
  constructor(result: Any?) : this(result != null, result)

  companion object {
    val NOT_FOUND = ParseResult(false)
  }
}

private fun ((KParameter, RoutingContext) -> ParseResult).then(fn: (KParameter, RoutingContext) -> ParseResult): (KParameter, RoutingContext) -> ParseResult {
  return { parameter, context ->
    val result = this(parameter, context)
    when {
      !result.found -> fn(parameter, context)
      else -> result
    }
  }
}

private val parameterParser = ::parsePathParameter
  .then(::parseQueryParameter)
  .then(::parseContextParameter)
  .then(::parseHeaderParameter)
  .then(::parseBodyParameter)

private fun KParameter.parseParameter(context: RoutingContext): Any? {
  try {
    return parameterParser(this, context).result
  } catch (ex: Throwable) {
    log.error("failed to parse parameter ${this.name}", ex)
    throw RuntimeException("failed to parse parameter ${this.name}: ${ex.message}", ex)
  }
}

private fun parseContextParameter(
  parameter: KParameter,
  context: RoutingContext
): ParseResult {
  try {
    parameter.findAnnotation<Context>() ?: return ParseResult.NOT_FOUND
    // we support either HttpHeaders or the User value
    return when {
      parameter.getType().isSubclassOf(HttpHeaders::class) -> {
        ParseResult(HttpHeadersImpl(context))
      }
      parameter.getType().isSubclassOf(User::class) -> {
        // specify that parameter is found even if context.user() is null --
        // the `@Context user: User?` parameter is nullable when users are unauthenticated
        ParseResult(true, context.user())
      }
      else -> error("expected parameter to be of type ${HttpHeaders::class.qualifiedName}")
    }
  } catch (ex: Throwable) {
    throw RuntimeException(
      "failed to parse context parameter for ${parameter.name}: ${ex.message}",
      ex
    )
  }
}

private fun parseHeaderParameter(
  parameter: KParameter,
  context: RoutingContext
): ParseResult {
  try {
    val annotation =
      parameter.findAnnotation<HeaderParam>() ?: return ParseResult.NOT_FOUND
    val headerName = annotation.value
    val values = context.request().headers().getAll(headerName)
    return when (parameter.type.classifier) {
      List::class -> ParseResult(parameter.parseHeaderValuesAsList(values))
      Set::class -> ParseResult(parameter.parseHeaderValuesAsSet(values))
      else -> {
        val result = parameter.type.parseHeaderValue(values.firstOrNull())
        assert(parameter.type.isMarkedNullable || result != null) { "method requires header $headerName but it was missing" }
        ParseResult(true, result)
      }
    }
  } catch (ex: Throwable) {
    throw RuntimeException(
      "failed to parse header parameter for ${parameter.name}: ${ex.message}",
      ex
    )
  }
}

private fun KParameter.parseHeaderValuesAsList(values: List<String>): List<*> {
  return values.map { this.type.arguments.first().parseHeaderValue(it) }
}

private fun KParameter.parseHeaderValuesAsSet(values: List<String>): Set<*> {
  return parseHeaderValuesAsList(values).toSet()
}

private fun KTypeProjection.parseHeaderValue(value: String?): Any? {
  val type = this.type
  return when (type) {
    null -> value
    else -> type.parseHeaderValue(value)!!
  }
}

private fun KType.parseHeaderValue(value: String?): Any? {
  return Converter.convert(value, this)
}

private fun parseQueryParameter(
  parameter: KParameter,
  context: RoutingContext
): ParseResult {
  try {
    val parameterName = parameter.parameterName() ?: return ParseResult.NOT_FOUND
    val queryParam = context.request().query().parseQueryParams()[parameterName]
    return when {
      queryParam != null -> {
        if (parameter.isSimpleType()) {
          // TODO: handle arrays
          ParseResult(parameter.parseSimpleType(URLDecoder.decode(queryParam, "UTF-8")))
        } else {
          parseComplexType(
            parameter,
            Buffer.buffer(URLDecoder.decode(queryParam, "UTF-8"))
          )
        }
      }
      else -> {
        ParseResult.NOT_FOUND
      }
    }
  } catch (ex: Throwable) {
    throw RuntimeException(
      "failed to parse query parameter ${parameter.name}: ${ex.message}",
      ex
    )
  }
}

private fun parsePathParameter(
  parameter: KParameter,
  context: RoutingContext
): ParseResult {
  try {
    return when {
      parameter.isSimpleType() -> {
        val parameterName = parameter.parameterName() ?: return ParseResult.NOT_FOUND
        val paramString = context.pathParam(parameterName)
        if (paramString == null) {
          ParseResult.NOT_FOUND
        } else {
          ParseResult(parameter.parseSimpleType(paramString))
        }
      }
      else -> {
        ParseResult.NOT_FOUND
      }
    }
  } catch (ex: Throwable) {
    throw RuntimeException(
      "failed to parse path parameter for ${parameter.name}: ${ex.message}",
      ex
    )
  }
}

private fun KParameter.isSimpleType(): Boolean {
  val k = this.getType()
  return (Number::class.isSuperclassOf(k) || k == String::class || k == Boolean::class)
}

internal fun KParameter.parameterName(): String? {
  return this.findAnnotation<Parameter>()?.name?.nonEmptyOrNull()
    ?: this.findAnnotation<QueryParam>()?.value?.nonEmptyOrNull()
    ?: this.findAnnotation<PathParam>()?.value?.nonEmptyOrNull()
    ?: this.findAnnotation<HeaderParam>()?.value?.nonEmptyOrNull()
    ?: this.findAnnotation<FormParam>()?.value?.nonEmptyOrNull()
    ?: this.findAnnotation<MatrixParam>()?.value?.nonEmptyOrNull()
    ?: this.name
}

internal fun String.nonEmptyOrNull() = when {
  this.isEmpty() -> null
  else -> this
}

private fun <R> KCallable<R>.validateParameters() {
  return this.parameters.forEach { it.validateParameter() }
}

private fun KParameter.validateParameter() {
  this.validateContextAnnotation()
  this.validateHeaderParamAnnotation()
}

private fun KParameter.validateHeaderParamAnnotation() {
  this.findAnnotation<HeaderParam>() ?: return
  val type = this.getType()
  when (type) {
    String::class -> {
    }
    List::class, Set::class -> {
//      val args = this.type.arguments
//      val argType = args.first().type!!.classifier as KClass<*>
//      assert(argType == String::class) { "the collection type for parameter ${this.name} should be String" }
    }
    else -> {
      error("parameter ${this.name} is not a String?, List<String>, or Set<String>")
    }
  }
}

private fun KParameter.validateContextAnnotation() {
  this.findAnnotation<Context>() ?: return
  assert(
    this.getType().isSubclassOf(HttpHeaders::class) ||
      this.getType().isSubclassOf(User::class)
  ) {
    "braid only supports @${HttpHeaders::class.simpleName} or @${User::class.simpleName} parameters annotated with @${Context::class.simpleName}, but parameter ${this.name} is of type ${this.getType()}"
  }
}