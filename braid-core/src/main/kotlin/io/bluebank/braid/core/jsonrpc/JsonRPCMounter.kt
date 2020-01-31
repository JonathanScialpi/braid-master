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

import io.bluebank.braid.core.jsonrpc.JsonRPCErrorResponse.Companion.invalidRequest
import io.bluebank.braid.core.jsonrpc.JsonRPCErrorResponse.Companion.serverError
import io.bluebank.braid.core.jsonrpc.JsonRPCErrorResponse.Companion.throwInvalidRequest
import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.service.MethodDoesNotExist
import io.bluebank.braid.core.service.ServiceExecutor
import io.bluebank.braid.core.socket.Socket
import io.bluebank.braid.core.socket.SocketListener
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.rx.java.RxHelper
import rx.Subscription

class JsonRPCMounter(private val executor: ServiceExecutor, vertx: Vertx) :
  SocketListener<JsonRPCRequest, JsonRPCResponse> {

  companion object {
    private val log = loggerFor<JsonRPCMounter>()
    const val MIN_VERSION = 2.0
  }

  private lateinit var socket: Socket<JsonRPCRequest, JsonRPCResponse>
  private val activeSubscriptions = mutableMapOf<Long, Subscription>()
  private val scheduler = RxHelper.scheduler(vertx)

  override fun onRegister(socket: Socket<JsonRPCRequest, JsonRPCResponse>) {
    this.socket = socket
  }

  override fun onData(
    socket: Socket<JsonRPCRequest, JsonRPCResponse>,
    item: JsonRPCRequest
  ) {
    item.withMDC {
      handleRequest(item)
    }
  }

  override fun onEnd(socket: Socket<JsonRPCRequest, JsonRPCResponse>) {
    activeSubscriptions.forEach { _, subscription -> subscription.unsubscribe() }
    activeSubscriptions.clear()
  }

  class FutureHandler(val callback: (AsyncResult<Any?>) -> Unit) :
    Handler<AsyncResult<Any?>> {

    override fun handle(event: AsyncResult<Any?>) {
      callback(event)
    }
  }

  private fun handleRequest(request: JsonRPCRequest) {
    request.withMDC {
      log.trace("handling request {}", request)
      try {
        checkVersion(request)
        if (request.isStreamCancelRequest()) {
          stopStream(request)
        } else {
          if (activeSubscriptions.containsKey(request.id)) {
            val err = invalidRequest(
              request.id,
              "a request with duplicate request id is in progress for this connection"
            )
            log.warn(err.error.message)
            throw JsonRPCException(err)
          }
          val subscription = executor.invoke(request)
            .observeOn(scheduler, true)
            .subscribe(
              { data -> handleDataItem(data, request) },
              { err -> handlerError(err, request) },
              { handleCompleted(request) }
            )
          activeSubscriptions[request.id] = subscription
        }
      } catch (err: JsonRPCException) {
        log.error("failed to handle request $request", err)
        err.response.send()
      }
    }
  }

  private fun stopStream(request: JsonRPCRequest) {
    request.withMDC {
      log.trace("cancelling stream")
      activeSubscriptions[request.id]?.apply {
        if (!this.isUnsubscribed) {
          this.unsubscribe()
        } else {
          log.trace("cannot cancel because subscription already unsubscribed")
        }
        activeSubscriptions.remove(request.id)
      } ?: run {
        log.trace("cannot cancel stream because no active subscription found")
      }
    }
  }

  private fun handleCompleted(request: JsonRPCRequest) {
    request.withMDC {
      try {
        if (request.streamed) {
          log.trace("sending completion message")
          val payload = JsonRPCCompletedResponse(id = request.id)
          socket.write(payload)
        } else {
          log.trace("handling completion. not streamed, therefore not sending anything")
        }
      } catch (err: Throwable) {
        log.error("failed to handle completion", err)
      } finally {
        activeSubscriptions.remove(request.id)
      }
    }
  }

  private fun handlerError(err: Throwable, request: JsonRPCRequest) {
    request.withMDC {
      try {
        log.trace(request.id, err) { "handling error result" }
        when (err) {
          is MethodDoesNotExist -> JsonRPCErrorResponse.methodNotFound(
            request.id,
            "method ${request.method} not implemented"
          ).send()
          is JsonRPCException -> err.response.send()
          else -> serverError(request.id, err.message).send()
        }
      } catch (err: Throwable) {
        log.error(request.id, err) { "failed to handle error" }
      } finally {
        activeSubscriptions.remove(request.id)
      }
    }
  }

  private fun handleDataItem(result: Any?, request: JsonRPCRequest) {
    request.withMDC {
      try {
        log.trace("sending data item back {}", result)
        val payload = JsonRPCResultResponse(result = result, id = request.id)
        socket.write(payload)
        if (!request.streamed) {
          log.trace("closing subscription", result)
          activeSubscriptions[request.id]?.apply {
            if (isUnsubscribed) {
              log.trace("subscription is already unsubscribed!")
            } else {
              unsubscribe()
            }
          } ?: run {
            log.trace("could not find active subscription")
          }
          log.trace("removing active subscription")
          activeSubscriptions.remove(request.id)
        }
      } catch (err: Throwable) {
        log.error("failed to handle data item $result", err)
      }
    }
  }

  private fun checkVersion(request: JsonRPCRequest) {
    val message = "braid version must be at least 2.0"
    try {
      val version = request.jsonrpc.toDouble()
      if (version < MIN_VERSION) {
        log.error("version $version is less than minimum version $MIN_VERSION")
        throwInvalidRequest(request.id, message)
      }
    } catch (err: NumberFormatException) {
      log.error("version ${request.jsonrpc} is not parsable to a double")
      throwInvalidRequest(request.id, message)
    }
  }

  private fun JsonRPCErrorResponse.send() {
    try {
      if (this.id != null && this.id is Long) {
        log.trace(this.id) { "sending error response: ${this.error}" }
      } else if (log.isTraceEnabled) {
        log.trace("sending error response: ${this.error}")
      }
      socket.write(this)
    } catch (err: Throwable) {
      if (this.id != null && this.id is Long) {
        log.error(id, err) { "failed to send error response" }
      } else if (log.isErrorEnabled) {
        log.error("failed to send error response", err)
      }
    }
  }
}