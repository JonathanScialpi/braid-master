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
package io.bluebank.braid.client.invocations.impl

import io.bluebank.braid.client.BraidClientConfig
import io.bluebank.braid.core.async.getOrThrow
import io.bluebank.braid.core.json.BraidJacksonInit
import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import io.bluebank.braid.core.jsonrpc.error
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.WebSocket
import io.vertx.core.http.WebSocketFrame
import io.vertx.core.json.Json
import java.net.URL

internal class InvocationsImpl internal constructor(
  val vertx: Vertx,
  config: BraidClientConfig,
  private val exceptionHandler: (Throwable) -> Unit,
  private val closeHandler: (() -> Unit),
  clientOptions: HttpClientOptions
) : InvocationsInternalImpl(
  config.serviceURI.toString(),
  InvocationStrategy.Companion::invoke
) {

  companion object {
    private val log = loggerFor<InvocationsImpl>()
    val defaultClientHttpOptions = HttpClientOptions()

    init {
      BraidJacksonInit.init()
    }
  }

  /**
   * connection to the server
   * */
  private var socket: WebSocket? = null

  private val client = vertx.createHttpClient(
    clientOptions
      .setDefaultHost(config.serviceURI.host)
      .setDefaultPort(config.serviceURI.port)
      .setSsl(config.tls)
      .setVerifyHost(config.verifyHost)
      .setTrustAll(config.trustAll)
  )

  init {
    // set up the websocket with all the required handlers

    val protocol = if (config.tls) "https" else "http"
    val url = URL(
      protocol,
      config.serviceURI.host,
      config.serviceURI.port,
      "${config.serviceURI.path}/websocket"
    )
    val result = Future.future<Boolean>()
    client.webSocket(url.toString()) { arWebSocket ->
      when {
        arWebSocket.succeeded() -> {
          val sock = arWebSocket.result()
          socket = arWebSocket.result()
          sock.handler(this::receive)
          sock.exceptionHandler(exceptionHandler)
          sock.closeHandler(closeHandler)
          result.complete(true)
        }
        else -> {
          log.error("failed to bind to websocket", arWebSocket.cause())
          socket = null
          result.fail(arWebSocket.cause())
        }
      }
    }
    result.getOrThrow()
  }

  /**
   * shutdown everything
   * after calling this all calls to [invoke] will fail with [IllegalStateException]
   */
  override fun close() {
    super.close()
    if (socket != null) {
      try {
        socket?.close()
      } catch (err: Throwable) {
        log.info("exception during closing of braid client socket", err)
      }
      socket = null
    }
    client.close()
  }

  /**
   * writes a [JsonRPCRequest] [request] on the socket to the server
   * @returns future to indicate if the send was succesful or not
   */
  override fun send(request: JsonRPCRequest): Future<Unit> {
    if (log.isTraceEnabled) {
      log.trace("writing request to socket {}", Json.encode(request))
    }

    val result = Future.future<Unit>()
    try {
      vertx.runOnContext { sendDirectOnThisContext(request, result) }
    } catch (err: Throwable) {
      log.error(request.id, err) { "failed to schedule send operation to context" }
      result.fail(err)
    }
    return result
  }

  private fun sendDirectOnThisContext(request: JsonRPCRequest, result: Future<Unit>) {
    try {
      socket
        ?.writeFrame(WebSocketFrame.textFrame(Json.encode(request), true))
        ?: error("socket was not created or was closed")
      try {
        result.complete()
      } catch (err: Throwable) {
        log.error(request.id, err) { "failed to send completion notification to handler" }
      }
    } catch (err: Throwable) {
      log.error(request.id, err) { "failed to send packet to socket" }
      result.fail(err)
    }
  }

  /**
   * syntactic sugar to set a kotlin function as the close handler of a [WebSocket]
   */
  private fun WebSocket.closeHandler(fn: () -> Unit) {
    this.closeHandler {
      fn()
    }
  }
}

