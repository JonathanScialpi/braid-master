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
package io.bluebank.braid.corda

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val logger: Logger = LoggerFactory.getLogger("driver-test")

fun main(args: Array<String>) {
    val user = User("user1", "test", permissions = setOf("ALL"))
    val bankA = CordaX500Name("BankA", "", "GB")
    val bankB = CordaX500Name("BankB", "", "US")
    logger.info("starting up cluster")
    driver(DriverParameters(
      isDebug = true,
      startNodesInProcess = true)) {
        val (partyA, partyB) = listOf(
          startNode(providedName = bankA, rpcUsers = listOf(user)),
          startNode(providedName = bankB, rpcUsers = listOf(user))
        ).map { it.getOrThrow() }
        logger.info("cluster started")
        val nodes = partyA.rpc.networkMapSnapshot().map { "[${it.legalIdentities.first().name}]" }
        logger.info("nodes in the network: ${nodes.joinToString(", ")}")
        logger.info("shutting down cluster")
        partyA.stop()
        partyB.stop()
        exitProcess(0)
    }
}