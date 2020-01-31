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

class JsonRPCException(val response: JsonRPCErrorResponse) : Exception() {
  @Throws(JsonRPCException::class)
  fun raise(): Nothing {
    throw this
  }
}

class JsonRPCError(val code: Int, val message: String) {
  companion object {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
    const val BASE_SERVER_ERROR = -32000 // to -32099
  }
}

data class JsonRPCErrorResponse(
  val error: JsonRPCError,
  val id: Any? = null,
  val jsonrpc: String = "2.0"
) : JsonRPCResponse() {

  constructor(id: Any?, message: String, code: Int) : this(
    JsonRPCError(code, message),
    id = id
  )

  companion object {

    fun internalError(id: Any?, message: String) =
      JsonRPCErrorResponse(id = id, message = message, code = JsonRPCError.INTERNAL_ERROR)

    fun parseError(message: String) =
      JsonRPCErrorResponse(id = null, message = message, code = JsonRPCError.PARSE_ERROR)

    fun throwInvalidRequest(id: Any?, message: String) {
      invalidRequest(id, message).asException().raise()
    }

    fun invalidRequest(id: Any?, message: String) =
      JsonRPCErrorResponse(
        id = id,
        message = message,
        code = JsonRPCError.INVALID_REQUEST
      )

    fun methodNotFound(id: Any?, message: String) =
      JsonRPCErrorResponse(
        id = id,
        message = message,
        code = JsonRPCError.METHOD_NOT_FOUND
      )

    fun invalidParams(id: Any?, message: String) =
      JsonRPCErrorResponse(id = id, message = message, code = JsonRPCError.INVALID_PARAMS)

    fun serverError(id: Any?, message: String?, offset: Int = 0) =
      JsonRPCErrorResponse(
        id = id,
        message = message ?: "unknown error",
        code = JsonRPCError.BASE_SERVER_ERROR - offset
      )
  }

  fun asException() = JsonRPCException(this)
}

fun Throwable.createJsonException(request: JsonRPCRequest) =
  JsonRPCErrorResponse.serverError(request.id, message).asException()

