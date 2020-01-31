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

import com.fasterxml.jackson.core.type.TypeReference
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.impl.NoStackTraceThrowable
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.handler.impl.HttpStatusException
import java.math.BigDecimal
import java.math.BigInteger

// extension methods for HttpClientResponse

inline fun <reified T : Any> HttpClientResponse.body(): Future<T> {
  return try {
    when (statusCode() / 100) {
      2 -> {
        val result = Future.future<Any>()
        this.bodyHandler { buffer ->
          try {
            when (T::class.java) {
              Buffer::class.java -> result.complete(buffer)
              String::class.java -> result.complete(buffer.toString())
              Int::class.java -> result.complete(buffer.toString().toInt())
              Long::class.java -> result.complete(buffer.toString().toLong())
              Float::class.java -> result.complete(buffer.toString().toFloat())
              Double::class.java -> result.complete(buffer.toString().toDouble())
              BigDecimal::class.java -> result.complete(buffer.toString().toBigDecimal())
              BigInteger::class.java -> result.complete(buffer.toString().toBigInteger())
              JsonObject::class.java -> result.complete(buffer.toJsonObject())
              JsonArray::class.java -> result.complete(buffer.toJsonArray())
              else -> result.complete(Json.decodeValue(buffer, object: TypeReference<T>() {}))
            }
          } catch (e: Throwable) {
            result.fail(e)
          }
        }
        @Suppress("UNCHECKED_CAST")
        result as Future<T>
      }
      else -> {
        Future.failedFuture(HttpStatusException(statusCode(), statusMessage()))
      }
    }
  } catch (e: Throwable) {
    Future.failedFuture(e)
  }
}

