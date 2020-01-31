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

import io.bluebank.braid.corda.server.BraidMain
import io.bluebank.braid.corda.server.BraidServerConfig
import io.bluebank.braid.core.utils.JarDownloader
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.toPath
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import net.corda.testing.node.internal.TestCordappInternal
import java.net.URL
import java.nio.file.Path

fun main(args: Array<String>) {
  run();
}

fun run() {
  val user = User("user1", "test", permissions = setOf("ALL"))
  val bankA = CordaX500Name("PartyA", "London", "GB")
  val bankB = CordaX500Name("PartyB", "New York", "US")

  val jarFiles = listOf(
//    "file:///Users/fuzz/dev/web3j/corda/console/src/test/resources/cordapps/obligation-0.1.jar",
    "https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda-finance-workflows/4.1/corda-finance-workflows-4.1.jar",
    "https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda-finance-contracts/4.1/corda-finance-contracts-4.1.jar"
  )

  val downloader = JarDownloader()

  val cordapps = jarFiles.map { downloader.uriToFile(URL(it)).toPath() }
    .map { TestCordappJar(jarFile = it) }

  driver(
    DriverParameters(
      cordappsForAllNodes = cordapps,
      waitForAllNodesToFinish = true,
      isDebug = true,
      startNodesInProcess = true
    )
  ) {
    // This starts two nodes simultaneously with startNode, which returns a future that completes when the node
    // has completed startup. Then these are all resolved with getOrThrow which returns the NodeHandle list.
    val (partyA, partyB) = listOf(
      startNode(providedName = bankA, rpcUsers = listOf(user)),
      startNode(providedName = bankB, rpcUsers = listOf(user))
    ).map { it.getOrThrow() }

    val braidMain = BraidMain()
    braidMain.start(
      BraidServerConfig(
        networkHostAndPort = partyA.rpcAddress,
        port = 8080,
        cordapps = jarFiles
      )
    ).compose {
      braidMain.start(
        BraidServerConfig(
          networkHostAndPort = partyB.rpcAddress,
          port = 8081,
          cordapps = jarFiles
        )
      )
    }
  }
}

class TestCordappJar(
  override val config: Map<String, Any> = emptyMap(),
  override val jarFile: Path
) :
  TestCordappInternal() {

  override fun withOnlyJarContents(): TestCordappInternal {
    return TestCordappJar(emptyMap(), jarFile)
  }

  override fun withConfig(config: Map<String, Any>): TestCordapp {
    return TestCordappJar(config, jarFile)
  }
}