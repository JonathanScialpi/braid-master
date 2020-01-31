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

import io.bluebank.braid.cordapp.echo.flows.EchoRespond
import io.bluebank.braid.cordapp.echo.flows.EchoSend
import io.bluebank.braid.cordapp.echo.states.EchoState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * This is untested and unfortunately doesn't work.
 * MockNetwork's cordappsForAllNodes requires that the cordapps exist as a JAR and/or in a gradle project.
 *
 * DriverParameters supports extraCordappPackagesToScan which can simply read class files
 * in the braid-corda/target/test-classes directory, but MockNetworkParameters doesn't.
 */

@Ignore
class EchoFlowTests {

  private val network = MockNetwork(
    MockNetworkParameters(
      cordappsForAllNodes = listOf(
        TestCordapp.findCordapp("io.bluebank.braid.cordapp.echo.contracts"),
        TestCordapp.findCordapp("io.bluebank.braid.cordapp.echo.flows")
      )
    )
  )
  private val a = network.createNode()
  private val b = network.createNode()

  init {
    listOf(a, b).forEach {
      it.registerInitiatedFlow(EchoSend.Responder::class.java)
      it.registerInitiatedFlow(EchoRespond.Responder::class.java)
    }
  }

  @Before
  fun setup() {
    network.runNetwork()
  }

  @After
  fun tearDown() {
    network.stopNodes()
  }

  @Test
  fun `flow rejects invalid input data`() {

    val flow = EchoSend.Initiator("   ", b.info.singleIdentity())
    val future = a.startFlow(flow)
    network.runNetwork()

    // The EchoContract specifies that message cannot be blank (i.e. all whitespace).
    assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
  }

  @Test
  fun `successful end-to-end`() {

    fun send(): EchoState {
      val flow = EchoSend.Initiator("Hello", b.info.singleIdentity())
      val future = a.startFlow(flow)
      network.runNetwork()
      return future.get()
    }

    fun respond(input: EchoState): EchoState {
      val numberOfEchos = 3; // pseudo-random
      val flow = EchoRespond.Initiator(input.linearId, numberOfEchos)
      val future = b.startFlow(flow)
      network.runNetwork()
      future.get()
      return input.withEchoes((numberOfEchos)) // expected output state
    }

    fun assert(expected: EchoState) {
      listOf(a, b).forEach { node ->
        val echoStateAndRef =
          node.services.vaultService.queryBy(EchoState::class.java).states.single()
        val echoState: EchoState = echoStateAndRef.state.data
        // assert that there's a state in the vault which matches the initial
        assert(expected == echoState)
      }
    }

    val initialState: EchoState = send();
    assert(initialState)
    val finalState: EchoState = respond(initialState)
    assert(finalState)
  }
}