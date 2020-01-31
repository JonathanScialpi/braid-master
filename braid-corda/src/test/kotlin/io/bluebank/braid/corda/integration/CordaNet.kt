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
package io.bluebank.braid.corda.integration

import io.bluebank.braid.corda.BraidCordaJacksonSwaggerInit
import io.bluebank.braid.core.logging.LogInitialiser
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.*

class CordaNet {

  companion object {
    init {
      LogInitialiser.init()
      BraidCordaJacksonSwaggerInit.init()
    }

    @JvmStatic
    fun main(args: Array<String>) {
      CordaNet().withCluster {
        readLine()
      }
    }

    fun createCordaNet(): CordaNet {
      return CordaNet()
    }
  }

  fun withCluster(callback: DriverDSL.() -> Unit) {
    val parties = listOf("PartyA")
    val portAllocation = PortAllocation.defaultAllocator

    // setup braid ports per party
    val systemProperties = setupBraidPortsPerParty(parties, portAllocation)

    driver(
      DriverParameters(
        isDebug = false,
        startNodesInProcess = true,
        portAllocation = portAllocation,
        systemProperties = systemProperties,
        extraCordappPackagesToScan = listOf(
          "net.corda.finance",
          "io.bluebank.braid.corda.integration.cordapp"
        )
      )
    ) {
      // start up the controller and all the parties
      val nodeHandles = startupNodes(parties)

      // run the rest of the programme
      callback()
      nodeHandles.map { it.stop() }
    }
  }

  fun braidPortForParty(party: String): Int {
    return System.getProperty("braid.$party.port")?.toInt()
      ?: error("could not locate braid port for $party")
  }

  private fun DriverDSL.startupNodes(parties: List<String>): List<NodeHandle> {
    // start the nodes sequentially, to minimise port clashes
    return parties.map { party ->
      startNode(providedName = CordaX500Name(party, "London", "GB")).getOrThrow()
    }
  }

  private fun setupBraidPortsPerParty(
    parties: List<String>,
    portAllocation: PortAllocation
  ): Map<String, String> {
    return parties
      .map { party -> "braid.$party.port" to portAllocation.nextPort().toString() }
      .toMap()
      .apply {
        println("braid port map")
        forEach {
          println("${it.key} = ${it.value}")
          System.setProperty(it.key, it.value)
        }
      }
  }
}

