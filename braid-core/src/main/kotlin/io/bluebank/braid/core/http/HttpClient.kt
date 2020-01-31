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

import io.vertx.core.Future
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.Json
import java.net.URLEncoder
import javax.ws.rs.core.MediaType

// vertx HttpClient extension methods
// these assume the client has been setup with default port and host

fun HttpClient.getFuture(
  path: String,
  headers: Map<String, Any> = emptyMap(),
  queryParameters: Map<String, Any> = emptyMap()
): Future<HttpClientResponse> {
  return requestFuture(HttpMethod.GET, path, headers, queryParameters)
}

fun HttpClient.postFuture(
  path: String,
  headers: Map<String, Any> = emptyMap(),
  queryParameters: Map<String, Any> = emptyMap(),
  body: Any? = null
): Future<HttpClientResponse> {
  return requestFuture(HttpMethod.POST, path, headers, queryParameters, body)
}

fun HttpClient.putFuture(
  path: String,
  headers: Map<String, Any> = emptyMap(),
  queryParameters: Map<String, Any> = emptyMap(),
  body: Any? = null
) = requestFuture(HttpMethod.PUT, path, headers, queryParameters, body)

fun HttpClient.patchFuture(
  path: String,
  headers: Map<String, Any> = emptyMap(),
  queryParameters: Map<String, Any> = emptyMap(),
  body: Any? = null
) = requestFuture(HttpMethod.PATCH, path, headers, queryParameters, body)

fun HttpClient.deleteFuture(
  path: String,
  headers: Map<String, Any> = emptyMap(),
  queryParameters: Map<String, Any> = emptyMap(),
  body: Any? = null
) = requestFuture(HttpMethod.DELETE, path, headers, queryParameters, body)

fun HttpClient.headFuture(
  path: String,
  headers: Map<String, Any> = emptyMap(),
  queryParameters: Map<String, Any> = emptyMap(),
  body: Any? = null
) = requestFuture(HttpMethod.HEAD, path, headers, queryParameters, body)

fun HttpClient.requestFuture(
  method: HttpMethod,
  path: String,
  headers: Map<String, Any> = emptyMap(),
  queryParameters: Map<String, Any> = emptyMap(),
  body: Any? = null
): Future<HttpClientResponse> {
  val result = Future.future<HttpClientResponse>()
  try {
    val modifiedPath = path.appendQueryParameters(queryParameters)
    @Suppress("DEPRECATION")
    this.request(method, modifiedPath)
      .apply {
        headers.forEach { (k, v) ->
          putHeader(k, v.toString())
        }
        when {
          body == null -> { }
          body.javaClass.isPrimitive || body is String -> putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
          else -> putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
        }
      }
      .exceptionHandler {
        // seems that vertx can occasionally fail a request twice
        if (!result.isComplete) {
          result.fail(it)
        }
      }
      .handler {
        result.complete(it)
      }
      .apply {
        when {
          body == null -> end()
          body.javaClass.isPrimitive || body is String -> {
            val payload = body.toString()
            putHeader(HttpHeaders.CONTENT_LENGTH, payload.length.toString())
            end(payload)
          }
          else -> {
            val payload = Json.encode(body)
            putHeader(HttpHeaders.CONTENT_LENGTH, payload.length.toString())
            end(payload)
          }
        }
      }
  } catch (e: Throwable) {
    result.fail(e)
  }
  return result
}

private fun String.appendQueryParameters(
  queryParameters: Map<String, Any>
): String {
  return when {
    queryParameters.isNotEmpty() -> {
      val interimPath = when {
        contains("?") -> this
        else -> "$this?"
      }
      val queryString = queryParameters.map { (name, value) ->
        val nameEnc = URLEncoder.encode(name, Charsets.UTF_8.name())
        val valueEnc = URLEncoder.encode(value.toString(), Charsets.UTF_8.name())
        "$nameEnc=$valueEnc"
      }.joinToString("&")
      interimPath + queryString
    }
    else -> this
  }
}