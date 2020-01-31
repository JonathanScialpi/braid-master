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
package io.bluebank.braid.cordapp.echo

import io.bluebank.braid.cordapp.echo.contracts.EchoContract
import io.bluebank.braid.cordapp.echo.states.EchoState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Ignore
import org.junit.Test

/**
 * This isn't a very thorough test, it's quite a simple contract.
 * Just an example of a successful test and of a failed test.
 */

@Ignore
class EchoContractTests {

  private val sender = TestIdentity(CordaX500Name("Sender Inc.", "London", "GB"))
  private val responder = TestIdentity(CordaX500Name("Responder Ltd.", "London", "GB"))
  private val ledgerServices = MockServices(
    listOf(
      "io.bluebank.braid.cordapp.echo.contracts",
      "io.bluebank.braid.cordapp.echo.flows"
    )
  )

  @Test
  fun `invalid input`() {
    ledgerServices.ledger {
      val badInput = EchoState("  ", sender.party, responder.party)
      // this generates a warning:
      // [WARN] 11:29:43,904 [Test worker] contracts.AttachmentConstraint.warnOnce - Found state io.bluebank.braid.cordapp.echo.contracts.EchoContract that is constrained by the insecure: AlwaysAcceptAttachmentConstraint.
      transaction {
        output(EchoContract.ID, badInput)
        command(sender.party.owningKey, EchoContract.Commands.Send())
        `fails with`("non-empty and not whitespace")
      }
    }
  }

  @Test
  fun `successful send`() {
    ledgerServices.ledger {
      val goodInput = EchoState("hello", sender.party, responder.party)
      // this generates a warning:
      // [WARN] 11:29:43,904 [Test worker] contracts.AttachmentConstraint.warnOnce - Found state io.bluebank.braid.cordapp.echo.contracts.EchoContract that is constrained by the insecure: AlwaysAcceptAttachmentConstraint.
      transaction {
        output(EchoContract.ID, goodInput)
        command(sender.party.owningKey, EchoContract.Commands.Send())
        verifies()
      }
    }
  }

  @Test
  fun `successful echo`() {
    ledgerServices.ledger {
      val goodInput = EchoState("hello", sender.party, responder.party)
      val goodOutput = goodInput.withEchoes(5)
      // this generates a warning:
      // [WARN] 11:29:43,904 [Test worker] contracts.AttachmentConstraint.warnOnce - Found state io.bluebank.braid.cordapp.echo.contracts.EchoContract that is constrained by the insecure: AlwaysAcceptAttachmentConstraint.
      transaction {
        input(EchoContract.ID, goodInput)
        output(EchoContract.ID, goodOutput)
        command(
          listOf(sender.party.owningKey, responder.party.owningKey),
          EchoContract.Commands.Respond()
        )
        verifies()
      }
    }
  }

  @Test
  fun `unsuccessful forgery`() {
    ledgerServices.ledger {
      val goodInput = EchoState("hello", sender.party, responder.party)
      val badOutput = goodInput.withEchoes(5).copy(message = "haha!")
      // this generates a warning:
      // [WARN] 11:29:43,904 [Test worker] contracts.AttachmentConstraint.warnOnce - Found state io.bluebank.braid.cordapp.echo.contracts.EchoContract that is constrained by the insecure: AlwaysAcceptAttachmentConstraint.
      transaction {
        input(EchoContract.ID, goodInput)
        output(EchoContract.ID, badOutput)
        command(
          listOf(sender.party.owningKey, responder.party.owningKey),
          EchoContract.Commands.Respond()
        )
        `fails with`("Input and output states are equal except the numberOfEchos")
      }
    }
  }
}