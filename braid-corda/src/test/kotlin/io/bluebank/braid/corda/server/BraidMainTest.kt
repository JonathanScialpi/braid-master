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
package io.bluebank.braid.corda.server

import io.bluebank.braid.corda.BraidCordaJacksonSwaggerInit
import io.bluebank.braid.corda.services.SimpleNodeInfo
import io.bluebank.braid.core.async.catch
import io.bluebank.braid.core.async.onSuccess
import io.bluebank.braid.core.http.body
import io.bluebank.braid.core.http.getFuture
import io.bluebank.braid.core.http.postFuture
import io.bluebank.braid.core.socket.findFreePort
import io.bluebank.braid.core.utils.JarDownloader
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import io.vertx.core.Future
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.toPath
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.AbstractCashFlow
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Ignore
import org.junit.runner.RunWith
import java.net.URL
import kotlin.test.Test

@Ignore
@RunWith(VertxUnitRunner::class)
class BraidMainTest {

  companion object {
    private val jarFiles = listOf(
      "https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda-finance-workflows/4.1/corda-finance-workflows-4.1.jar",
      "https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda-finance-contracts/4.1/corda-finance-contracts-4.1.jar"
    )
    private val cordapps = JarDownloader().let { downloader ->
      jarFiles.map { downloader.uriToFile(URL(it)).toPath() }
        .map { TestCordappJar(jarFile = it) }
    }

    private val user = User("user1", "test", permissions = setOf("ALL"))
    private val bankA = CordaX500Name("PartyA", "London", "GB")
    private val bankB = CordaX500Name("PartyB", "New York", "US")

    init {
      BraidCordaJacksonSwaggerInit.init()
    }
  }

  private val braidAPort = findFreePort()
  private val braidBPort = findFreePort()

  @Test
  fun `test that we can invoke an issuance`(context: TestContext) {
    val async = context.async()
    withNetwork(context) { braid ->
      val client = braid.createHttpClient(braidAPort)
      getSwaggerModel(client)
        .onSuccess { openApi -> verifyOpenApi(context, openApi) }
        .compose { client.getFuture("/api/rest/network/nodes") }
        .compose { it.body<Array<SimpleNodeInfo>>() }
        .map { nodes ->
          nodes.map { node -> node.legalIdentities.first().name to node.legalIdentities.first() }
            .toMap()
        }
        .compose { nodes ->
          val payload = mapOf(
            "amount" to Amount.parseCurrency("£100.00"),
            "issueRef" to OpaqueBytes.of(0x01),
            "recipient" to nodes[bankB],
            "anonymous" to false,
            "notary" to nodes[DUMMY_NOTARY_NAME]
          )
          client.postFuture(
            path = "/api/rest/cordapps/corda-finance-workflows/flows/net.corda.finance.flows.CashIssueAndPaymentFlow",
            body = payload
          )
        }
        .compose { it.body<AbstractCashFlow.Result>() }
        .onSuccess {
//          println(it)
        }
        .compose {

          Future.succeededFuture<Unit>()
        }
    }
      .onSuccess { async.complete() }
      .catch { context.fail(it) }
  }

  private fun BraidMain.createHttpClient(port: Int) =
    vertx.createHttpClient(
      HttpClientOptions().setDefaultPort(port).setDefaultHost("localhost").setSsl(
        true
      ).setTrustAll(true).setVerifyHost(false)
    )

  private fun verifyOpenApi(context: TestContext, openApi: OpenAPI) {
    context.assertEquals(1, openApi.servers.size)
    context.assertEquals(
      "http://localhost:${braidAPort}/api/rest",
      openApi.servers[0].url
    )
    context.assertTrue(openApi.paths.containsKey("/network/nodes"))
    context.assertTrue(openApi.paths.containsKey("/cordapps"))
    context.assertTrue(openApi.paths.containsKey("/cordapps/corda-finance-workflows/flows/net.corda.finance.flows.CashPaymentFlow"))
    context.assertTrue(openApi.components.schemas.containsKey("Error"))
    context.assertTrue(openApi.components.schemas.containsKey("net.corda.finance.flows.AbstractCashFlow_Result"))
  }

  private fun getSwaggerModel(client: HttpClient): Future<OpenAPI> {
    return client.getFuture("/swagger.json")
      .compose { it.body<String>() }
      .map { Json.mapper().readValue(it, OpenAPI::class.java) }
  }

  private fun <T> withNetwork(
    context: TestContext,
    fn: (BraidMain) -> Future<T>
  ): Future<T> {
    return driver(
      DriverParameters(
        cordappsForAllNodes = cordapps,
        waitForAllNodesToFinish = false,
        isDebug = true,
        startNodesInProcess = true
      )
    ) {
      val (partyA, partyB) = listOf(
        startNode(providedName = bankA, rpcUsers = listOf(user)),
        startNode(providedName = bankB, rpcUsers = listOf(user))
      ).map { it.getOrThrow() }

      val braidMain = BraidMain()
      var result: T? = null

      val async = context.async()
      setupBraidServers(braidMain, partyA, partyB)
        .compose { fn(braidMain) }
        .onSuccess { result = it }
        .compose { braidMain.shutdown() }
        .map { result!! }
        .onSuccess { async.complete() }
        .catch { context.fail(it) }
        .also { async.await() }
    }
  }

  private fun setupBraidServers(
    braidMain: BraidMain,
    partyA: NodeHandle,
    partyB: NodeHandle
  ): Future<String> {
    return braidMain.start(
      BraidServerConfig(
        networkHostAndPort = partyA.rpcAddress,
        port = braidAPort,
        cordapps = jarFiles
      )
    ).compose {
      braidMain.start(
        BraidServerConfig(
          networkHostAndPort = partyB.rpcAddress,
          port = braidBPort,
          cordapps = jarFiles
        )
      )
    }
  }
}

