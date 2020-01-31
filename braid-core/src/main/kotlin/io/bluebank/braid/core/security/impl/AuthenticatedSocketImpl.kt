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
package io.bluebank.braid.core.security.impl

import io.bluebank.braid.core.jsonrpc.JsonRPCErrorResponse
import io.bluebank.braid.core.jsonrpc.JsonRPCErrorResponse.Companion.invalidParams
import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import io.bluebank.braid.core.jsonrpc.JsonRPCResultResponse
import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.security.AuthenticatedSocket
import io.bluebank.braid.core.security.AuthenticatedSocket.Companion.LOGIN_METHOD
import io.bluebank.braid.core.security.AuthenticatedSocket.Companion.LOGOUT_METHOD
import io.bluebank.braid.core.security.AuthenticatedSocket.Companion.MSG_FAILED
import io.bluebank.braid.core.security.AuthenticatedSocket.Companion.MSG_PARAMETER_ERROR
import io.bluebank.braid.core.socket.AbstractSocket
import io.bluebank.braid.core.socket.Socket
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.auth.User

class AuthenticatedSocketImpl(
  private val authProvider: AuthProvider
) :
  AbstractSocket<Buffer, Buffer>(), AuthenticatedSocket {

  companion object {
    private val log = loggerFor<AuthenticatedSocketImpl>()
  }

  private var user: User? = null
  private lateinit var socket: Socket<Buffer, Buffer>

  override fun user(): User? {
    return user
  }

  override fun onRegister(socket: Socket<Buffer, Buffer>) {
    this.socket = socket
    this.user = null
  }

  override fun onData(socket: Socket<Buffer, Buffer>, item: Buffer) {
    log.trace("decoding potential auth payload")
    val op = Json.decodeValue(item, JsonRPCRequest::class.java)
    op.withMDC {
      log.trace("decoded {}", op)
      when (op.method) {
        LOGIN_METHOD -> handleAuthRequest(op)
        LOGOUT_METHOD -> {
          log.trace("logout received - un-authenticating this connection")
          user = null
          sendOk(op)
        }
        else -> {
          // this isn't an auth op, so if we're logged in, then pass it on
          if (user != null) {
            onData(item)
          } else {
            val msg =
              JsonRPCErrorResponse.serverError(id = op.id, message = "not authenticated")
            write(Json.encodeToBuffer(msg))
          }
        }
      }
    }
  }

  override fun onEnd(socket: Socket<Buffer, Buffer>) {
    onEnd()
  }

  override fun write(obj: Buffer): Socket<Buffer, Buffer> {
    socket.write(obj)
    return this
  }

  @Suppress("UNCHECKED_CAST")
  private fun handleAuthRequest(op: JsonRPCRequest) {
    log.trace("handling login auth request")
    if (op.params == null || op.params !is List<*> || op.params.size != 1 || op.params.first() !is Map<*, *>) {
      sendParameterError(op)
    } else {
      val m = op.params.first() as Map<String, Any>
      authProvider.authenticate(JsonObject(m)) {
        op.withMDC {
          if (it.succeeded()) {
            user = it.result()
            sendOk(op)
          } else {
            user = null
            sendFailed(op, MSG_FAILED)
          }
        }
      }
    }
  }

  private fun sendParameterError(op: JsonRPCRequest) {
    try {
      val msg = invalidParams(
        id = op.id,
        message = MSG_PARAMETER_ERROR
      )
      log.error(msg.error.message)
      write(Json.encodeToBuffer(msg))
    } catch (err: Throwable) {
      log.error("failed to write to socket during sendParameterError")
    }
  }

  private fun sendOk(op: JsonRPCRequest) {
    val msg = JsonRPCResultResponse(id = op.id, result = "OK")
    write(Json.encodeToBuffer(msg))
  }

  private fun sendFailed(op: JsonRPCRequest, message: String) {
    val msg = JsonRPCErrorResponse.serverError(id = op.id, message = message)
    write(Json.encodeToBuffer(msg))
  }
}