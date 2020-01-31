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
package io.bluebank.braid.corda.standalone

import io.bluebank.braid.core.utils.toJarsClassLoader
import io.bluebank.braid.core.utils.tryWithClassLoader
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User

/*
 Use this with BraidTest -DcordaStarted=true when running locally

 to speed up the
  */

// CordaStandalonekt

fun main(args: Array<String>) {
  // provide a list of cordaApp jars  including obligsation

  val jars = args.toList()

  tryWithClassLoader(jars.toJarsClassLoader()) {
    startStandalone()
  }

}

private fun startStandalone() {
  val user = User("user1", "test", permissions = setOf("ALL"))
  val bankA = CordaX500Name("BankA", "", "GB")
  val bankB = CordaX500Name("BankB", "", "US")

  driver(
    DriverParameters(
      cordappsForAllNodes = listOf(
        TestCordapp.findCordapp("net.corda.finance.contracts.asset"),
        TestCordapp.findCordapp("net.corda.finance.schemas"),
        TestCordapp.findCordapp("net.corda.finance.flows")
        //       ,TestCordapp.findCordapp("net.corda.examples.obligation")
      ),
      waitForAllNodesToFinish = true,
      isDebug = false,
      startNodesInProcess = true
    )
  ) {
    // This starts two nodes simultaneously with startNode, which returns a future that completes when the node
    // has completed startup. Then these are all resolved with getOrThrow which returns the NodeHandle list.
    val (partyA, partyB) = listOf(
      startNode(providedName = bankA, rpcUsers = listOf(user)),
      startNode(providedName = bankB, rpcUsers = listOf(user))
    ).map { it.getOrThrow() }

    println("partyA rpc: ${partyA.rpcAddress}")
    println("partyB rpc: ${partyB.rpcAddress}")
  }
}

