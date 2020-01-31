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
package io.bluebank.braid.corda.services

import io.bluebank.braid.corda.BraidConfig
import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import io.bluebank.braid.core.jsonschema.toDescriptor
import io.bluebank.braid.core.jsonschema.toSimpleJavascriptType
import io.bluebank.braid.core.service.MethodDescriptor
import io.bluebank.braid.core.service.MethodDoesNotExist
import io.bluebank.braid.core.service.ServiceExecutor
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.FlowStateMachine
import net.corda.core.toObservable
import net.corda.core.utilities.ProgressTracker
import net.corda.node.services.api.StartedNodeServices
import rx.Observable
import java.lang.reflect.Constructor

class CordaFlowServiceExecutor(
  private val services: FlowStarterAdapter,
  val config: BraidConfig
) : ServiceExecutor {

  override fun invoke(request: JsonRPCRequest): Observable<Any> {
    val flow = config.registeredFlows[request.method]
    return if (flow != null) {
      invoke(request, flow)
    } else {
      Observable.error(MethodDoesNotExist(request.method))
    }
  }

  override fun getStubs(): List<MethodDescriptor> {
    return config.registeredFlows.flatMap { flow ->
      // we filter constructors with progress trackers for now
      // there may be a correct way of handling these but for now, these are not included
      // constructor parameters are denoted as discrete methods appended with the number of parameters that the
      // constructor takes,
      flow.value.constructors.filter {
        !it.parameterTypes.contains(ProgressTracker::class.java)
      }.map {
        val returnType = flow.value.getMethod("call").returnType
        it.toDescriptor()
          .copy(name = flow.key, returnType = returnType.toSimpleJavascriptType())
      }
    }
  }

  private fun invoke(
    request: JsonRPCRequest,
    clazz: Class<out FlowLogic<*>>
  ): Observable<Any> {
    val constructor = clazz.constructors.firstOrNull { it.matches(request) }
    return if (constructor == null) {
      Observable.error(MethodDoesNotExist(request.method))
    } else {
      @Suppress("DEPRECATION")
      return Observable.create { subscriber ->
        try {
          val params = request.mapParams(constructor)
          services.startFlowDynamic(clazz, *params).returnValue
            .toObservable().subscribe({ item ->
              subscriber.onNext(item)
            }, { err ->
              subscriber.onError(err)
            }, {
              subscriber.onCompleted()
            })
        } catch (err: Throwable) {
          subscriber.onError(err)
        }
      }
    }
  }
}

private fun Constructor<*>.matches(request: JsonRPCRequest) =
  this.parameterCount == request.paramCount()

private fun <T> StartedNodeServices.startFlowDynamic(flow: FlowLogic<T>): CordaFuture<FlowStateMachine<T>> {
  val context =
    InvocationContext.service(this.javaClass.name, myInfo.legalIdentities[0].name)
  return this.startFlow(flow, context)
}