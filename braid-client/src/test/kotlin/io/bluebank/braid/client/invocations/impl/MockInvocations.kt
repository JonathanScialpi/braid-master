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

import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import io.bluebank.braid.core.jsonrpc.JsonRPCResponse
import io.vertx.core.Future
import io.vertx.core.json.Json
import java.util.concurrent.atomic.AtomicInteger

internal typealias WriteCallback = MockInvocations.(JsonRPCRequest) -> Future<Unit>

internal class MockInvocations(
  invocationTarget: InvocationTarget = InvocationStrategy.Companion::invoke,
  private val writeCallback: WriteCallback = { Future.succeededFuture(Unit) }
) : InvocationsInternalImpl(invocationTarget = invocationTarget) {

  private val invocationsCounter = AtomicInteger(0)
  private val cancellationsCounter = AtomicInteger(0)
  private val requestsList = mutableListOf<JsonRPCRequest>()
  var lastRequestId: Long = -1
    private set

  val invocationsCount get() = invocationsCounter.get()
  val cancellationsCount get() = cancellationsCounter.get()
  val requests get() = requestsList.toList()

  /**
   * send a request to the network
   */
  override fun send(request: JsonRPCRequest): Future<Unit> {
    requestsList.add(request)
    lastRequestId = request.id
    when {
      request.method == JsonRPCRequest.CANCEL_STREAM_METHOD -> cancellationsCounter.incrementAndGet()
      else -> invocationsCounter.incrementAndGet()
    }
    return writeCallback(request)
  }

  fun receive(response: JsonRPCResponse) {
    receive(Json.encodeToBuffer(response))
  }
}