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
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party

interface ISay {
  fun hello(): String
}

interface IGeneric<T> {
  fun hello(): T
}

/**
 * This is for testing a flow class which has various exotic types of constructor parameter
 */
@InitiatingFlow
@StartableByRPC
class EchoFlowExotic(
  @Parameter(description = "This is an annotation on what would normally be a \$ref type")
  say: ISay,
  @Schema(description = "This is another annotation on what's normally be a \$ref type")
  generic: IGeneric<String>,
  words: Array<String>,
  parties: Set<Party>
) : FlowLogic<Unit>() {

  @Suspendable
  override fun call(): Unit {
  }
}
