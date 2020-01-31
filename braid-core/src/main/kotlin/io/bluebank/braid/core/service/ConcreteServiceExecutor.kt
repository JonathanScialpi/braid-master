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
package io.bluebank.braid.core.service

import io.bluebank.braid.core.jsonrpc.JsonRPCMounter
import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import io.bluebank.braid.core.jsonrpc.createJsonException
import io.bluebank.braid.core.jsonschema.toDescriptor
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import rx.Observable
import rx.Subscriber
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.javaType

class ConcreteServiceExecutor(private val service: Any) : ServiceExecutor {
  companion object {
    private val log = loggerFor<ConcreteServiceExecutor>()
  }

  override fun invoke(request: JsonRPCRequest): Observable<Any> {
    @Suppress("DEPRECATION")
    return Observable.create<Any> { subscriber ->
      request.withMDC {
        try {
          log.trace("binding to method for {}", request)
          candidateMethods(request)
            .asSequence() // lazy sequence
            .convertParametersAndFilter(request)
            .map { (method, params) ->
              if (log.isTraceEnabled) {
                log.trace(
                  "invoking ${method.asSimpleString()} with ${params.joinToString(
                    ","
                  ) { it.toString() }}"
                )
              }
              method.call(service, *params).also {
                if (log.isTraceEnabled) {
                  log.trace(
                    "successfully invoked ${method.asSimpleString()} with ${params.joinToString(
                      ","
                    ) { it.toString() }}"
                  )
                }
              }
            }
            .firstOrNull()
            ?.also { result -> handleResult(result, request, subscriber) }
            ?: throwMethodDoesNotExist(request)
        } catch (err: InvocationTargetException) {
          log.error("failed to invoke target for $request", err)
          subscriber.onError(err.targetException)
        } catch (err: Throwable) {
          log.error("failed to invoke $request", err)
          subscriber.onError(err)
        }
      }
    }
  }

  private fun KFunction<*>.asSimpleString(): String {
    val params = this.parameters.drop(1)
      .joinToString(",") { "${it.name}: ${it.type.javaType.typeName}" }
    return "$name($params)"
  }

  private fun candidateMethods(request: JsonRPCRequest): List<KFunction<*>> {
    return service::class.functions
      .filter(request::matchesName)
      .filter(this::isPublic)
      .filter { it.parameters.size == request.paramCount() + 1 }
      .map { it to request.computeScore(it) }
      .filter { (_, score) -> score > 0 }
      .sortedByDescending { (_, score) -> score }
      .also { candidates ->
        if (log.isTraceEnabled) {
          log.trace("scores for candidate methods for {}:", request)
          candidates.forEach { candidate ->
            println("${candidate.second}: ${candidate.first.asSimpleString()}")
          }
        }
      }
      .map { (fn, _) -> fn }
  }

  private fun throwMethodDoesNotExist(request: JsonRPCRequest) {
    throw MethodDoesNotExist("failed to find a method that matches ${request.method}(${request.paramsAsString()})")
  }

  private fun Sequence<KFunction<*>>.convertParametersAndFilter(request: JsonRPCRequest): Sequence<Pair<KFunction<*>, Array<Any?>>> {
    // attempt to convert the parameters
    return map { method ->
      method to try {
        request.mapParams(method)
      } catch (err: Throwable) {
        log.warn("parameter does not match for method:" + method)
        null
      }
    }
      // filter out parameters that didn't match
      .filter { (_, params) -> params != null }
      .map { (method, params) -> method to params!! }
  }

  @Suppress("UNCHECKED_CAST")
  private fun handleResult(
    result: Any?,
    request: JsonRPCRequest,
    subscriber: Subscriber<Any>
  ) {
    log.trace("handling result {}", request.id, result)
    when (result) {
      is Future<*> -> handleFuture(result as Future<Any>, request, subscriber)
      is Observable<*> -> handleObservable(result as Observable<Any>, request, subscriber)
      else -> respond(result, subscriber)
    }
  }

  private fun handleObservable(
    result: Observable<Any>,
    request: JsonRPCRequest,
    subscriber: Subscriber<Any>
  ) {
    log.trace("{} - handling observable result", request.id)
    result
      .onErrorResumeNext { err -> Observable.error(err.createJsonException(request)) }
      .let {
        // insert logger if trace is enabled
        request.withMDC {
          if (log.isTraceEnabled) {
            it
              .doOnNext {
                log.trace("sending item {}", it)
              }
              .doOnError {
                log.trace("sending error {}", it)
              }
              .doOnCompleted {
                log.trace("completing stream")
              }
          } else {
            it
          }
        }
      }
      .subscribe(subscriber)
  }

  private fun handleFuture(
    future: Future<Any>,
    request: JsonRPCRequest,
    callback: Subscriber<Any>
  ) {
    request.withMDC {
      log.trace("{} - handling future result", request.id)
      future.setHandler(JsonRPCMounter.FutureHandler {
        handleAsyncResult(it, request, callback)
      })
    }
  }

  private fun handleAsyncResult(
    response: AsyncResult<*>,
    request: JsonRPCRequest,
    subscriber: Subscriber<Any>
  ) {
    request.withMDC {
      log.trace("{} - handling async result of invocation", request.id)
      when (response.succeeded()) {
        true -> respond(response.result(), subscriber)
        else -> respond(response.cause().createJsonException(request), subscriber)
      }
    }
  }

  private fun respond(result: Any?, subscriber: Subscriber<Any>) {
    log.trace("sending result and completing {}", result)
    subscriber.onNext(result)
    subscriber.onCompleted()
  }

  private fun respond(err: Throwable, subscriber: Subscriber<Any>) {
    log.trace("sending error {}", err)
    subscriber.onError(err)
  }

  private fun isPublic(method: KFunction<*>) = method.visibility == KVisibility.PUBLIC

  override fun getStubs(): List<MethodDescriptor> {
    return service.javaClass.declaredMethods
      .filter { Modifier.isPublic(it.modifiers) }
      .filter { !it.name.contains("$") }
      .map { it.toDescriptor() }
  }
}