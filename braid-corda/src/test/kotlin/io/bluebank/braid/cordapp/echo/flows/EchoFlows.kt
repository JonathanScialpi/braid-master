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
import com.google.common.collect.ImmutableList
import io.bluebank.braid.cordapp.echo.contracts.EchoContract
import io.bluebank.braid.cordapp.echo.states.EchoState
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * I'm not sure how to choose the return-type for a top-level (parent) flow like Initiator
 * I think it could be Unit, and that its type is chosen only to make it possible to unit-test the flow?!
 */

// this initiates an echo -- transaction is signed by the sender only
object EchoSend {

  @InitiatingFlow
  @StartableByRPC
  class Initiator(
    @Schema(description = "The message to be echoed")
    private val message: String,
    @Schema(description = "The counter-party which will respond with an echo")
    private val responder: Party
  ) : FlowLogic<EchoState>() {

    override val progressTracker = ProgressTracker()

    @Operation(
      summary = "Initiate an echo",
      description = "Send a message to a responder, which will echo it in a subsequent flow"
    )
    @Suspendable
    override fun call(): EchoState {
      val notary = serviceHub.networkMapCache.notaryIdentities[0] // semi-random notary
      val outputState = EchoState(message, ourIdentity, responder)
      // sending only needs one signature
      val command = Command(EchoContract.Commands.Send(), ourIdentity.owningKey)
      val txBuilder = TransactionBuilder(notary = notary)
        .addOutputState(outputState, EchoContract.ID)
        .addCommand(command)
      txBuilder.verify(serviceHub)
      val signedTx: SignedTransaction = serviceHub.signInitialTransaction(txBuilder)
      val otherPartySession = initiateFlow(responder)
      subFlow(FinalityFlow(signedTx, otherPartySession))
      return outputState
    }
  }

  @InitiatedBy(Initiator::class)
  class Responder(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
      subFlow(ReceiveFinalityFlow(otherPartySession))
    }
  }
}

// this echoes a response -- transaction is signed by both parties
object EchoRespond {

  @InitiatingFlow
  @StartableByRPC
  class Initiator(
    @Parameter(description = "The initiated EchoState instance to be echoed")
    private val linearId: UniqueIdentifier,
    @Parameter(description = "The payload echoed with the message back to the initiator")
    private val numberOfEchos: Int
  ) : FlowLogic<Unit>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call() {

      val inputStateAndRef: StateAndRef<EchoState> = getMessageByLinearId(linearId)
      val inputState: EchoState = inputStateAndRef.state.data
      val outputState = inputState.withEchoes(numberOfEchos) // create output from input
      val notary = inputStateAndRef.state.notary // reuse original notary
      // for the sake of experimenting with different flows,
      // EchoContract.verify specifies that the Respond command requires both signatures
      val command = Command(
        EchoContract.Commands.Respond(),
        inputState.participants.map { it.owningKey })
      val txBuilder = TransactionBuilder(notary = notary)
        .addInputState(inputStateAndRef)
        .addOutputState(outputState, EchoContract.ID)
        .addCommand(command)
      txBuilder.verify(serviceHub)
      // todo not sure why 2nd parameter to signInitialTransaction need be specified
      val mySignature = inputState.responder.owningKey
      val partiallySignedTx = serviceHub.signInitialTransaction(txBuilder, mySignature)
      // get 2nd signature, from the original sender
      val otherPartySession = initiateFlow(outputState.sender)
      val fullySignedTx = subFlow(
        CollectSignaturesFlow(
          partiallySignedTx,
          setOf(otherPartySession),
          listOf(mySignature)
          // progressTracker
        )
      )
      subFlow(FinalityFlow(fullySignedTx, otherPartySession))
    }

    private fun getMessageByLinearId(linearId: UniqueIdentifier): StateAndRef<EchoState> {
      val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
        null,
        ImmutableList.of(linearId),
        Vault.StateStatus.UNCONSUMED, null
      )

      return serviceHub.vaultService.queryBy<EchoState>(queryCriteria).states.singleOrNull()
        ?: throw FlowException("EchoState with id $linearId not found.")
    }

  }

  @InitiatedBy(Initiator::class)
  class Responder(private val otherPartySession: FlowSession) :
    FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
      val stx = subFlow(object : SignTransactionFlow(otherPartySession) {
        override fun checkTransaction(stx: SignedTransaction) {
          // Check the transaction is signed apart from our own key and the notary
          stx.verifySignaturesExcept(ourIdentity.owningKey, stx.tx.notary!!.owningKey)
          // Check the transaction data is correctly formed
          val ltx = stx.toLedgerTransaction(serviceHub, false)
          ltx.verify()
          // Confirm that this is the expected type of transaction
          require(ltx.commands.single().value is EchoContract.Commands.Respond) {
            "Transaction must represent an echo response"
          }
          // Check the context dependent parts of the transaction as the
          // Contract verify method must not use serviceHub queries.
          val state = ltx.outRef<EchoState>(0)
          require(serviceHub.myInfo.isLegalIdentity(state.state.data.sender)) {
            "Proposal not one of our original echo sends"
          }
          require(state.state.data.responder == otherPartySession.counterparty) {
            "Proposal not for sent from correct source"
          }
        }
      })
      return subFlow(ReceiveFinalityFlow(otherPartySession, stx.id))
    }
  }
}