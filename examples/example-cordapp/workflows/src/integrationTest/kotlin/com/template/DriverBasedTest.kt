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
package com.template

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import kotlin.test.assertEquals

class DriverBasedTest {
  val bankA = TestIdentity(CordaX500Name("BankA", "", "GB"))
  val bankB = TestIdentity(CordaX500Name("BankB", "", "US"))

  @Test
  fun `node test`() {
    driver(DriverParameters(isDebug = true, startNodesInProcess = true)) {
      // This starts two nodes simultaneously with startNode, which returns a future that completes when the node
      // has completed startup. Then these are all resolved with getOrThrow which returns the NodeHandle list.
      val (partyAHandle, partyBHandle) = listOf(
        startNode(providedName = bankA.name),
        startNode(providedName = bankB.name)
      ).map { it.getOrThrow() }

      // This test makes an RPC call to retrieve another node's name from the network map, to verify that the
      // nodes have started and can communicate. This is a very basic test, in practice tests would be starting
      // flows, and verifying the states in the vault and other important metrics to ensure that your CorDapp is
      // working as intended.
      assertEquals(
        partyAHandle.rpc.wellKnownPartyFromX500Name(bankB.name)!!.name,
        bankB.name
      )
      assertEquals(
        partyBHandle.rpc.wellKnownPartyFromX500Name(bankA.name)!!.name,
        bankA.name
      )
    }
  }
}