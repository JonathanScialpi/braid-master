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
package io.bluebank.braid.cordapp.echo.flows

import co.paralleluniverse.fibers.Suspendable
import io.bluebank.braid.cordapp.echo.states.EchoState
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party

/*
  This file contains a copy of the flows in EchoFlows.kt

  It exists because I want to test these flow classes repeatedly,
  using different SynthesisOptions.ObjectStrategy values,
  and I don't quite trust Swagger and/or Braid to not cache
  the type and/or the models generated from a type,
  so to ensure the tests are independent I use different copies of the classes.
 */

@InitiatingFlow
@StartableByRPC
class EchoSend1(
  @Schema(description = "The message to be echoed")
  private val message: String,
  @Schema(description = "The counter-party which will respond with an echo")
  private val responder: Party
) : FlowLogic<EchoState>() {

  @Operation(
    summary = "Initiate an echo",
    description = "Send a message to a responder, which will echo it in a subsequent flow"
  )
  @Suspendable
  override fun call(): EchoState {
    throw NotImplementedError()
  }
}

@InitiatingFlow
@StartableByRPC
class EchoSend2(
  @Schema(description = "The message to be echoed")
  private val message: String,
  @Schema(description = "The counter-party which will respond with an echo")
  private val responder: Party
) : FlowLogic<EchoState>() {

  @Operation(
    summary = "Initiate an echo",
    description = "Send a message to a responder, which will echo it in a subsequent flow"
  )
  @Suspendable
  override fun call(): EchoState {
    throw NotImplementedError()
  }
}

@InitiatingFlow
@StartableByRPC
class EchoSendExtra(
  @Schema(description = "The message to be echoed")
  private val message: String,
  @Schema(description = "The counter-party which will respond with an echo")
  private val responder: Party
) : FlowLogic<EchoState>() {

  @Operation(
    summary = "Initiate an echo",
    description = "Send a message to a responder, which will echo it in a subsequent flow"
  )
  @Suspendable
  override fun call(): EchoState {
    throw NotImplementedError()
  }
}

@InitiatingFlow
@StartableByRPC
class EchoRespond1(
  @Parameter(description = "The initiated EchoState instance to be echoed")
  private val linearId: UniqueIdentifier,
  @Parameter(description = "The payload echoed with the message back to the initiator")
  private val numberOfEchos: Int
) : FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    throw NotImplementedError()
  }
}

@InitiatingFlow
@StartableByRPC
class EchoRespond2(
  @Parameter(description = "The initiated EchoState instance to be echoed")
  private val linearId: UniqueIdentifier,
  @Parameter(description = "The payload echoed with the message back to the initiator")
  private val numberOfEchos: Int
) : FlowLogic<Unit>() {

  @Suspendable
  override fun call() {
    throw NotImplementedError()
  }
}
