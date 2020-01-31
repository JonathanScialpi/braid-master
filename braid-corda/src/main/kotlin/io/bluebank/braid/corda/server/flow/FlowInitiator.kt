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
package io.bluebank.braid.corda.server.flow

import io.bluebank.braid.corda.server.progress.ProgressNotification
import io.bluebank.braid.corda.server.rpc.RPCCallable
import io.bluebank.braid.corda.server.rpc.RPCInvocationParameter
import io.bluebank.braid.corda.services.FlowStarterAdapter
import io.bluebank.braid.core.async.toFuture
import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.synth.*
import io.vertx.core.Future
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.Json
import io.vertx.ext.auth.User
import net.corda.core.flows.FlowLogic
import net.corda.core.toObservable
import net.corda.core.utilities.ProgressTracker
import javax.ws.rs.core.Context
import kotlin.reflect.KCallable
import kotlin.reflect.KClass

class FlowInitiator(
  private val getFlowStarter: (User?) -> FlowStarterAdapter,
  private val eventBus: EventBus,
  private val isAuth: Boolean
) {

  companion object {
    val TOPIC = "braid-progress-tracker-message-topic-id"
  }

  private val log = loggerFor<FlowInitiator>()

  fun getInitiator(kClass: KClass<*>): KCallable<Future<Any?>> {
    val constructor = kClass.java.preferredConstructor()
    val additionalAnnotations = getFlowMethodAnnotations(kClass)

    // this is a simulated `@Context` instance -- cheat like this, to create it, because
    // `@Context` is difficult to instantiate, because it's an interface and final

    // trampoline is to make the kClass constructor look like a callable function
    val fn = trampoline(
      constructor = constructor,
      boundTypes = createBoundParameterTypes(),
      additionalAnnotations = additionalAnnotations,
      // This says that `@Context user: User` is an additional parameter; I couldn't make
      // it work properly as a `User?` type, so don't specify it at all if `!isAuth`.
      additionalParams = listOf(RPCInvocationParameter.invocationId()) + possibleUserParameter()
    ) {
      // this is passed to the transform parameter of the trampoline function
      // it's the body of the function which is invoked at run-time
        parameters ->
      // do what you want here ...
      // e.g. call the flow directly
      // obviously, we will be invoking the flow via an interface to CordaRPCOps or ServiceHub
      // and return a Future

      // because of additionalParams above, expect this extra `user` parameter at run-time
      val invocationId = parameters.first() as String?
      val user: User? = if (isAuth) parameters.get(1) as User else null

      // drop the user parameter, and filter out the ProgressTracker if there is one
      val excludeProgressTracker = parameters
        .drop(if (isAuth) 2 else 1)
        .filter { p -> p !is ProgressTracker }
      log.info("About to start $kClass with args: ${listOf(parameters)}")

      // get the FlowStarterAdapter instance which wraps this user's RPC connection
      val flowStarter: FlowStarterAdapter = getFlowStarter(user)

      val flowProgress = flowStarter.startTrackedFlowDynamic(
        kClass.java as Class<FlowLogic<*>>,
        *excludeProgressTracker.toTypedArray()
      )
      val notification =
        ProgressNotification().withInvocationId(invocationId).withFlowClass(kClass.java)
      flowProgress.progress.subscribe(
        { step -> eventBus.publish(TOPIC, Json.encode(notification.withStep(step))) },
        { error -> eventBus.publish(TOPIC, Json.encode(notification.withError(error))) },
        { eventBus.publish(TOPIC, Json.encode(notification.withComplete(true))) }
      )

      @Suppress("UNCHECKED_CAST")
      flowProgress.returnValue.toObservable().toFuture()
    }

    // RPCCallable is a KCallable instance (which can act as a path handler)
    return RPCCallable(kClass, fn)
  }

  private fun possibleUserParameter() =
    if (!isAuth) emptyList() else listOf(
      KParameterSynthetic(
        "user",
        User::class.java,
        listOf(Context::class.createAnnotationProxy())
      )
    )

  private fun createBoundParameterTypes(): Map<Class<*>, Any> {
    return mapOf<Class<*>, Any>(ProgressTracker::class.java to ProgressTracker())
  }
}
