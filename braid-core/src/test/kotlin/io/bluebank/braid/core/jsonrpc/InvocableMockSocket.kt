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

import io.bluebank.braid.core.async.getOrThrow
import io.bluebank.braid.core.async.toFuture
import io.vertx.core.Future
import rx.Observable
import rx.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KCallable

/**
 *  Nominal implementation of socket together with invocation capability
 */
class InvocableMockSocket : MockSocket<JsonRPCRequest, JsonRPCResponse>() {

  companion object {
    private val nextId = AtomicLong(1)
  }

  private val subject = PublishSubject.create<JsonRPCResponse>()

  init {
    addResponseListener(subject::onNext)
  }

  /**
   * the next unique (enough) id
   */
  fun nextId(): Long = nextId.getAndIncrement()

  fun <R> invoke(callable: KCallable<R>, vararg params: Any?): R {
    return invoke(nextId(), callable, *params)
  }

  fun <R> invoke(id: Long, callable: KCallable<R>, vararg params: Any?): R {
    return invoke(id, callable.name, *params)
  }

  fun <R> invoke(id: Long, name: String, vararg params: Any?): R {
    val future = setupPipeline<R>(id).first().toFuture()
    process(JsonRPCRequest(id = id, method = name, params = params.toList()))
    return future.getOrThrow()
  }

  fun <R> invoke(callable: KCallable<Future<R>>, vararg params: Any?): Future<R> {
    return invoke(nextId(), callable, *params)
  }

  fun <R> invoke(
    id: Long,
    callable: KCallable<Future<R>>,
    vararg params: Any?
  ): Future<R> {
    val future = setupPipeline<R>(id).first().toFuture()

    process(JsonRPCRequest(id = id, method = callable.name, params = params.toList()))
    return future
  }

  fun <R> invoke(callable: KCallable<Observable<R>>, vararg params: Any?): Observable<R> {
    return invoke(nextId(), callable, *params)
  }

  fun <R> invoke(
    id: Long,
    callable: KCallable<Observable<R>>,
    vararg params: Any?
  ): Observable<R> {
    val result = setupPipeline<R>(id)
      .doOnUnsubscribe { process(JsonRPCRequest.cancelRequest(id)) }

    process(
      JsonRPCRequest(
        id = id,
        method = callable.name,
        params = params.toList(),
        streamed = true
      )
    )
    return result
  }

  private fun <T> setupPipeline(id: Long): Observable<T> {
    return subject
      .filter { matchesId(it, id) }
      .takeWhile { it !is JsonRPCCompletedResponse }
      .map {
        when (it) {
          is JsonRPCResultResponse -> it.result
          is JsonRPCErrorResponse -> throw it.asException()
          else -> throw RuntimeException("unknown type $it")
        }
      }
      .map {
        @Suppress("UNCHECKED_CAST")
        it as T
      }
  }

  private fun matchesId(
    it: JsonRPCResponse,
    id: Long
  ): Boolean {
    return when (it) {
      is JsonRPCResultResponse -> it.id == id
      is JsonRPCErrorResponse -> it.id == id
      is JsonRPCCompletedResponse -> it.id == id
      else -> false
    }
  }

}