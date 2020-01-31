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
package io.bluebank.braid.cordapp.echo.contracts

import io.bluebank.braid.cordapp.echo.states.EchoState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class EchoContract : Contract {
  companion object {
    // Used to identify our contract when building a transaction.
    const val ID = "io.bluebank.braid.cordapp.echo.contracts.EchoContract"
  }

  // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
  // does not throw an exception.
  override fun verify(tx: LedgerTransaction) {
    // Verification logic goes here.
    val command = tx.commands.requireSingleCommand<Commands>()
    val outputState = tx.outputsOfType<EchoState>().single()

    // command-specific verification
    when (command.value) {
      is Commands.Send -> requireThat {
        "The echo must not yet have a response." using (outputState.numberOfEchos == null)
        "Sending an echo consumes no input states." using (tx.inputs.isEmpty())
        "Only the sender need be a signer." using (command.signers.size == 1 && command.signers[0] == outputState.sender.owningKey)
      }
      is Commands.Respond -> requireThat {
        "The echo must now have a response." using (outputState.numberOfEchos != null)
        "Sending an echo consumes one input state." using (tx.inputs.size == 1)
        val inputState = tx.inputsOfType<EchoState>().single()
        "Input has not been echoed yet." using (inputState.numberOfEchos == null)
        "Input and output states are equal except the numberOfEchos." using (inputState == outputState.withoutEchoes())
        "All of the participants must be signers." using (command.signers.containsAll(
          outputState.participants.map { it.owningKey }))
      }
      else ->
        throw NotImplementedError("Unexpected command type.")
    }

    // command-independent invariants
    requireThat {
      "Only one output state should be created." using (tx.outputs.size == 1)
      // or these two could be moved to `is Commands.Send ->` because when `is Commands.Respond ->` we check that echoed state is similar to sent state
      "The sender and the responder cannot be the same entity." using (outputState.sender != outputState.responder)
      "The echo's message must be non-empty and not whitespace." using (outputState.message.isNotBlank())
    }
  }

  // Used to indicate the transaction's intent.
  interface Commands : CommandData {

    class Send : Commands
    class Respond : Commands
  }
}
