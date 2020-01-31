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
package io.bluebank.braid.cordapp.echo.states

import io.bluebank.braid.cordapp.echo.contracts.EchoContract
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party

@BelongsToContract(EchoContract::class)
data class EchoState private constructor(
  @Parameter(description = "The message to be echoed")
  val message: String,
  @Schema(description = "The payload sent by the responder in the response flow")
  val numberOfEchos: Int?,
  @Schema(description = "The initiator of the message")
  val sender: Party,
  @Schema(description = "The responder which echoes the message")
  val responder: Party,
  override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {

  constructor(
    message: String,
    sender: Party,
    responder: Party
  ) : this(message, null, sender, responder) {
  }

  fun withEchoes(numberOfEchos: Int) = copy(numberOfEchos = numberOfEchos)
  fun withoutEchoes() = copy(numberOfEchos = null)

  override val participants get() = listOf(sender, responder)
}
