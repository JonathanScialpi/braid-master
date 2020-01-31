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
package io.bluebank.braid.core.jsonrpc

import org.slf4j.Logger
import org.slf4j.MDC
import java.lang.reflect.Constructor
import kotlin.reflect.KFunction

data class JsonRPCRequest(
  val jsonrpc: String = "2.0",
  val id: Long,
  val method: String,
  val params: Any?,
  val streamed: Boolean = false
) {

  companion object {
    const val MDC_REQUEST_ID = "braid-id"
    const val CANCEL_STREAM_METHOD = "_cancelStream"
    fun cancelRequest(id: Long) = JsonRPCRequest(
      id = id,
      method = CANCEL_STREAM_METHOD,
      params = null,
      streamed = false
    )
  }

  private val parameters = Params.build(params)

  fun paramCount(): Int = parameters.count

  fun matchesName(method: KFunction<*>): Boolean = method.name == this.method

  fun mapParams(method: KFunction<*>): Array<Any?> {
    return parameters.mapParams(method).toTypedArray()
  }

  fun mapParams(constructor: Constructor<*>): Array<Any?> {
    return parameters.mapParams(constructor).toTypedArray()
  }

  fun paramsAsString() = parameters.toString()
  fun computeScore(fn: KFunction<*>) = parameters.computeScore(fn)
  /**
   * SLF4J MDC logging context for this request object. Adds a [MDC_REQUEST_ID] value
   * to the MDC during the execution of [fn]
   */
  fun <R> withMDC(fn: () -> R): R {
    return withMDC(id, fn)
  }

  fun isStreamCancelRequest() = method == CANCEL_STREAM_METHOD
}

fun <R> withMDC(id: Long, fn: () -> R): R {
  val idString = id.toString()
  val currentValue = MDC.get(JsonRPCRequest.MDC_REQUEST_ID)
  return when {
    currentValue != null && currentValue == idString -> fn()
    else -> MDC.putCloseable(JsonRPCRequest.MDC_REQUEST_ID, idString).use {
      fn()
    }
  }
}

inline fun Logger.trace(requestId: Long, crossinline fn: () -> Any?) {
  if (isTraceEnabled) {
    withMDC(requestId) {
      trace(fn().toString())
    }
  }
}

inline fun Logger.trace(requestId: Long, err: Throwable, crossinline fn: () -> Any?) {
  if (isTraceEnabled) {
    withMDC(requestId) {
      trace(fn().toString(), err)
    }
  }
}

inline fun Logger.warn(requestId: Long, crossinline fn: () -> Any?) {
  if (isWarnEnabled) {
    withMDC(requestId) {
      warn(fn().toString())
    }
  }
}

inline fun Logger.warn(requestId: Long, err: Throwable, crossinline fn: () -> Any?) {
  if (isWarnEnabled) {
    withMDC(requestId) {
      warn(fn().toString(), err)
    }
  }
}

inline fun Logger.error(requestId: Long, crossinline fn: () -> Any?) {
  if (isErrorEnabled) {
    withMDC(requestId) {
      error(fn().toString())
    }
  }
}

inline fun Logger.error(requestId: Long, err: Throwable, crossinline fn: () -> Any?) {
  if (isErrorEnabled) {
    withMDC(requestId) {
      error(fn().toString(), err)
    }
  }
}

inline fun Logger.info(requestId: Long, crossinline fn: () -> Any?) {
  if (isInfoEnabled) {
    withMDC(requestId) {
      info(fn().toString())
    }
  }
}

inline fun Logger.info(requestId: Long, err: Throwable, crossinline fn: () -> Any?) {
  if (isInfoEnabled) {
    withMDC(requestId) {
      info(fn().toString(), err)
    }
  }
}

