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
import io.bluebank.braid.corda.server.progress.ProgressNotification
import io.bluebank.braid.corda.services.SimpleNodeInfo
import io.bluebank.braid.corda.services.vault.VaultQuery
import io.bluebank.braid.corda.util.VertxMatcher.vertxAssertThat
import io.bluebank.braid.corda.utils.toVertxFuture
import io.bluebank.braid.core.async.all
import io.bluebank.braid.core.async.catch
import io.bluebank.braid.core.async.mapUnit
import io.bluebank.braid.core.async.onSuccess
import io.bluebank.braid.core.http.body
import io.bluebank.braid.core.http.getFuture
import io.bluebank.braid.core.http.postFuture
import io.bluebank.braid.core.socket.findFreePort
import io.vertx.core.Future
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.Async
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.impl.TestContextImpl
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.handler.impl.HttpStatusException
import net.corda.core.contracts.ContractState
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.Builder.greaterThanOrEqual
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.AMOUNT
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.internal.withTestSerializationEnvIfNotSet
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import org.hamcrest.CoreMatchers.*
import org.junit.AfterClass
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.ws.rs.core.Response
import kotlin.test.assertEquals

/*
 * This test will:
 * - Start Corda
 * - Start Braid with user-login enabled and run tests
 * - Restart Braid with user-login disabled and rerun tests
 * When you want to run this test repeatedly can alternatively prestart Corda
 * (e.g. by running ../standalone/CordaStandalone.kt
 * and set the DcordaStarted property e.g. -DcordaStarted=true on the Java command-line),
 * otherwise it takes about 45 seconds or more to run.
 * See also ./experiment/TestSuite.kt which prototypes the architecture of this suite.
 *
 * Prestarting Corda isn't always tested recently.
 * There's a new `driver!!.dsl.defaultNotaryIdentity` expression in one of the test cases.
 */

/**
 * This class just starts Corda in its beforeClass function --
 * or doesn't if Corda is pre-started --
 * after which @RunWith(Suite::class) runs the identified SuiteClasses.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
  SuiteClassStandaloneServerAuth::class,
  SuiteClassStandaloneServerShared::class
)
class BraidCordaStandaloneServerTest {

  companion object {

    init {
      BraidCordaJacksonSwaggerInit.init()
    }

    private val log = loggerFor<BraidCordaStandaloneServerTest>()
    val isCordaPrestarted =
      System.getProperty("cordaStarted")?.toBoolean() ?: false
    private val user = User("user1", "test", permissions = setOf("ALL"))
    val bankA = CordaX500Name("BankA", "", "GB")
    val bankB = CordaX500Name("BankB", "", "US")

    val clientVertx = Vertx.vertx()
    var driver: TestDriver? = null
    var nodeA: NodeHandle? = null
    var nodeB: NodeHandle? = null

    fun beforeClassWithContext(testContext: TestContext) {
      val finished: Async = testContext.async()
      when {
        isCordaPrestarted -> succeededFuture(Unit)
        else -> startNodes()
      }
        .onSuccess { finished.complete() }
        .catch { testContext.fail(it.cause) }
    }

    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      /*
       * This class is @RunWith(Suite::class)
       * so it can't be @RunWith(VertxUnitRunner::class)
       * so the @BeforeClass function has no `testContext: TestContext` parameter
       * so `beforeClass` does not have the @BeforeClass annotation and instead this
       * function tries to do what VertxUnitRunner.invokeExplosively would do --
       * i.e. create a testContext with which to call beforeClassWithContext.
       *
       * This is a hack.
       * An alternative could be to create a new class named VertxSuiteRunner which
       * would include VertxUnitRunner and Suite behaviour, but
       * this hack is fewer lines of code so possibly easier and/or more maintainable.
       */

      // https://github.com/vert-x3/vertx-unit/blob/master/src/main/java/io/vertx/ext/unit/junit/VertxUnitRunner.java
      val testContext = TestContextImpl(emptyMap(), null)
      val future = CompletableFuture<Throwable>();
      val test = Handler<TestContext> { beforeClassWithContext(it) }
      val eh = Handler<Throwable> { future.complete(it) }
      log.trace("beforeClass starting")
      testContext.run(
        null,
        120_000,
        test,
        eh
      )
      log.trace("beforeClass waiting")
      val failure: Throwable? = try {
        future.get()
      } catch (e: InterruptedException) {
        // Should we do something else ?
        Thread.currentThread().interrupt()
        throw e
      } finally {
        // currentRunner.set(null)
      }
      log.trace("beforeClass ending")
      if (failure != null) {
        throw failure
      }
    }

    private fun startNodes(): Future<Unit> {
      driver = TestDriver.driver(
        DriverParameters(
          cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("net.corda.finance.contracts.asset"),
            TestCordapp.findCordapp("net.corda.finance.schemas"),
            TestCordapp.findCordapp("net.corda.finance.flows")
          ),
          isDebug = true, startNodesInProcess = true
        )
      )
      val nodeAFuture = driver!!.startNode(providedName = bankA, rpcUsers = listOf(user))
      val nodeBFuture = driver!!.startNode(providedName = bankB, rpcUsers = listOf(user))
      return all(nodeAFuture.toVertxFuture(), nodeBFuture.toVertxFuture())
        .onSuccess {
          nodeA = it.first()
          nodeB = it.last()
//          println("partyAHandle:${nodeA!!.rpcAddress}")
        }
        .mapUnit()
    }

    @AfterClass
    @JvmStatic
    fun closeDown() {
      log.trace("closeDown()")
      val testContext: TestContext = TestContextImpl(emptyMap(), null)
      if (driver != null) driver!!.close()
      // todo instead perhaps clientVertx should be local to each suite class
      clientVertx.close(testContext.asyncAssertSuccess())
    }
  }
}

/*
 * All the properties like `driver` which are currently in the companion object of the
 * BraidCordaStandaloneServerTest class could instead have been defined outside any class
 * at the top of this file.
 * In that case, all classes in this file would have been able to see/share them.
 *
 * Instead I encapsulate them list this, so that it will be possible to define suite
 * classes in other files.
 * If you create a new suite class, then let it use the properties of this object,
 * and add it to the @Suite.SuiteClasses annotation.
 */
object BraidCordaTestEnvironment {
  /*
    Properties initialised by BraidCordaStandaloneServerTest
    Implemented as get() functions so they're not called until run time
    so I needn't predict which objects are initialised first
   */

  val isCordaPrestarted: Boolean
    get() = BraidCordaStandaloneServerTest.isCordaPrestarted
  val bankA: CordaX500Name
    get() = BraidCordaStandaloneServerTest.bankA
  val bankB: CordaX500Name
    get() = BraidCordaStandaloneServerTest.bankB
  val nodeA: NodeHandle?
    get() = BraidCordaStandaloneServerTest.nodeA
  val nodeB: NodeHandle?
    get() = BraidCordaStandaloneServerTest.nodeB
  val clientVertx: Vertx
    get() = BraidCordaStandaloneServerTest.clientVertx
  val driver: TestDriver?
    get() = BraidCordaStandaloneServerTest.driver
  val nodeAPort: Int
    get() {
      return when {
        nodeA != null -> nodeA!!.p2pAddress.port
        else -> 10_004
      }
    }

  val nodeBPort: Int
    get() {
      return when {
        nodeB != null -> nodeB!!.p2pAddress.port
        else -> 10_004
      }
    }

  val notaryAddress: NetworkHostAndPort
    get() {
      return when {
        driver != null -> driver!!.dsl.defaultNotaryHandle.nodeHandles.getOrThrow().first().p2pAddress
        else -> NetworkHostAndPort("localhost", 10_000)
      }
    }
}

data class BraidCordaStandaloneServerSetup(
  val loginToken: String?,
  val client: HttpClient,
  val port: Int
)

class SuiteClassStandaloneServerAuth : SuiteClassStandaloneServerEither(setup) {

  companion object {
    private lateinit var setup: BraidCordaStandaloneServerSetup

    @BeforeClass
    @JvmStatic
    fun beforeClass(testContext: TestContext) {
      log.trace("BraidCordaStandaloneServerAuth beforeClass starting")
      val finished = testContext.async()
      setup = startBraidAndLogin(testContext, true)
      log.trace("BraidCordaStandaloneServerAuth beforeClass finished")
      finished.complete()
    }

    @AfterClass
    @JvmStatic
    fun closeDown() {
      log.trace("BraidCordaStandaloneServerAuth closeDown")
      setup.client.close()
    }
  }
}

/**
 * This is identical to BraidCordaStandaloneServerAuth except userMustLogin is false
 */
class SuiteClassStandaloneServerShared : SuiteClassStandaloneServerEither(setup) {

  companion object {
    private lateinit var setup: BraidCordaStandaloneServerSetup

    @BeforeClass
    @JvmStatic
    fun beforeClass(testContext: TestContext) {
      log.trace("BraidCordaStandaloneServerAuth beforeClass starting")
      val finished = testContext.async()
      setup = startBraidAndLogin(testContext, false)
      log.trace("BraidCordaStandaloneServerAuth beforeClass finished")
      finished.complete()
    }

    @AfterClass
    @JvmStatic
    fun closeDown() {
      log.trace("BraidCordaStandaloneServerAuth closeDown")
      setup.client.close()
    }
  }
}

@RunWith(VertxUnitRunner::class)
open class SuiteClassStandaloneServerEither(private val setup: BraidCordaStandaloneServerSetup) {

  companion object {
    val log = loggerFor<SuiteClassStandaloneServerEither>()

    /*
      * Define synonyms for the properties defined in BraidCordaTestEnvironment
      */

    val isCordaPrestarted: Boolean get() = BraidCordaTestEnvironment.isCordaPrestarted
    val bankA: CordaX500Name get() = BraidCordaTestEnvironment.bankA
    val bankB: CordaX500Name get() = BraidCordaTestEnvironment.bankB
    val nodeA: NodeHandle? get() = BraidCordaTestEnvironment.nodeA
    val nodeB: NodeHandle? get() = BraidCordaTestEnvironment.nodeB
    val clientVertx: Vertx get() = BraidCordaTestEnvironment.clientVertx
    val driver: TestDriver? get() = BraidCordaTestEnvironment.driver
    val nodeAPort: Int get() = BraidCordaTestEnvironment.nodeAPort
    val nodeBPort: Int get() = BraidCordaTestEnvironment.nodeBPort
    val notaryAddress: NetworkHostAndPort get() = BraidCordaTestEnvironment.notaryAddress

    /**
     * Start or restart Braid and maybe login as well
     */
    fun startBraidAndLogin(
      testContext: TestContext,
      userMustLogin: Boolean
    ): BraidCordaStandaloneServerSetup {
      val port: Int = findFreePort()
      val networkHostAndPort: NetworkHostAndPort = if (isCordaPrestarted)
        NetworkHostAndPort("localhost", 10005) else nodeA!!.rpcAddress
      var loginToken: String? = null
      val client = clientVertx.createHttpClient(HttpClientOptions().apply {
        defaultHost = "localhost"
        defaultPort = port
        isSsl = true
        isVerifyHost = false
        isTrustAll = true
      })
      log.trace("startBraidAndLogin starting")
      val async = testContext.async()
      startBraid(networkHostAndPort, userMustLogin, port)
        .onSuccess {
          if (userMustLogin) {
            loginToken = client.login(testContext)
            log.trace("loginToken is $loginToken")
            async.complete()
          } else {
            log.trace("loginToken is null")
            async.complete()
          }
        }
        .catch { testContext.fail(it.cause) }
      log.trace("startBraidAndLogin waiting")
      async.awaitSuccess()
      log.trace("startBraidAndLogin finished")
      return BraidCordaStandaloneServerSetup(loginToken, client, port)
    }

    private fun startBraid(
      networkHostAndPort: NetworkHostAndPort,
      userMustLogin: Boolean,
      port: Int
    ): Future<String> {
      // compile time check that we can inherit from BraidCordaStandaloneServer
      return BraidCordaStandaloneServer(
        userName = if (userMustLogin) "" else "user1",
        password = if (userMustLogin) "" else "test",
        port = port,
        nodeAddress = networkHostAndPort
      ).startServer()
    }
  }

  val loginToken = setup.loginToken
  val client = setup.client
  val port = setup.port

  @Test
  fun shouldListNetworkNodes(context: TestContext) {
    val async = context.async()
    log.trace("calling get: http://localhost:$port/api/rest/network/nodes")
    client.getFuture(
      "/api/rest/network/nodes",
      headers = mapOf("Accept" to "application/json; charset=utf8")
        .addBearerToken(loginToken)
    )
      .compose { it.body<List<SimpleNodeInfo>>() }
      .onSuccess { nodes ->
        context.assertThat(nodes.size, equalTo(3))
        val nodeInfoA =
          nodes.first { node -> node.legalIdentities.any { party -> party.name == bankA } }
        context.assertThat(
          nodeInfoA.addresses,
          hasItem(NetworkHostAndPort("localhost", nodeAPort))
        )
        val nodeInfoB =
          nodes.first { node -> node.legalIdentities.any { party -> party.name == bankB } }
        context.assertThat(
          nodeInfoB.addresses,
          hasItem(NetworkHostAndPort("localhost", nodeBPort))
        )
        val nodeInfoNotary =
          nodes.first { node -> node.legalIdentities.any { party -> party == driver!!.dsl.defaultNotaryIdentity } }
        context.assertThat(nodeInfoNotary.addresses, hasItem(notaryAddress))
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }

  @Test
  fun shouldListNetworkNodesByHostAndPort(context: TestContext) {
    val async = context.async()
    log.trace("calling get: https://localhost:$port/api/rest/network/nodes")
    client.getFuture(
      "/api/rest/network/nodes",
      headers = mapOf("Accept" to "application/json; charset=utf8")
        .addBearerToken(loginToken),
      queryParameters = mapOf("host-and-port" to "localhost:${nodeBPort}")
    )
      .compose { it.body<JsonArray>() }
      .onSuccess { nodes ->
        val node = nodes.getJsonObject(0)
        val addresses = node.getJsonArray("addresses")
        context.assertThat(addresses.size(), equalTo(1))
        context.assertThat(
          addresses.getJsonObject(0).getString("host"),
          equalTo("localhost")
        )
        context.assertThat(
          addresses.getJsonObject(0).getInteger("port"),
          equalTo(nodeBPort)
        )
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }

  @Test
  fun shouldGetPartyB() {
    val cordaX500Name = CordaX500Name("PartyB", "New York", "US")
    val toString = cordaX500Name.toString()
    val encode = URLEncoder.encode(toString, "UTF-8")
    assertThat(encode, `is`("O%3DPartyB%2C+L%3DNew+York%2C+C%3DUS"))
  }

  @Test
  fun shouldDecode() {
    val encode = URLDecoder.decode("O%3DPartyB%2CL%3DNew+York%2CC%3DUS", "UTF-8")
    val parse = CordaX500Name.parse(encode)
    val cordaX500Name = CordaX500Name("PartyB", "New York", "US")
    assertThat(parse, `is`(cordaX500Name))
  }

  @Test
  fun shouldListNetworkNodesByX509Name(context: TestContext) {
    val async = context.async()
    log.trace("calling get: https://localhost:$port/api/rest/network/nodes")
    client.getFuture(
      "/api/rest/network/nodes",
      queryParameters = mapOf("x500-name" to driver!!.dsl.defaultNotaryIdentity.name.toString()),
      headers = mapOf("Accept" to "application/json; charset=utf8")
        .addBearerToken(loginToken)
    )
      .compose { it.body<JsonArray>() }
      .onSuccess { nodes ->
        assertThat(nodes.size(), equalTo(1))
        val node = nodes.getJsonObject(0)
        val addresses = node.getJsonArray("addresses")
        context.assertThat(addresses.size(), equalTo(1))
        context.assertThat(
          addresses.getJsonObject(0).getString("host"),
          equalTo("localhost")
        )
        context.assertThat(
          addresses.getJsonObject(0).getInteger("port"),
          equalTo(notaryAddress.port)
        )
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }

  @Test
  fun `should return empty list if node not found`(context: TestContext) {
    val async = context.async()

    log.trace("calling get: https://localhost:$port/api/rest/network/nodes")
    client.getFuture(
      "/api/rest/network/nodes?x500-name=O%3DPartyB%2CL%3DNew+York%2CC%3DUS",
      mapOf("Accept" to "application/json; charset=utf8")
        .addBearerToken(loginToken)
    )
      // .exceptionHandler(context::fail)
      .onSuccess {
        context.assertEquals(200, it.statusCode(), it.statusMessage())

        it.bodyHandler {
          val nodes = it.toJsonArray()
          context.assertThat(nodes.size(), equalTo(0))
          async.complete()
        }
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }

  @Test
  fun shouldListSelf(context: TestContext) {
    val async = context.async()

    log.trace("calling get: https://localhost:$port/api/rest/network/nodes/self")
    client.getFuture(
      "/api/rest/network/nodes/self",
      headers = mapOf("Accept" to "application/json; charset=utf8")
        .addBearerToken(loginToken)
    )
      .compose { it.body<JsonObject>() }
      .onSuccess { node ->
        val addresses = node.getJsonArray("addresses")
        context.assertThat(addresses.size(), equalTo(1))
        context.assertThat(
          addresses.getJsonObject(0).getString("host"),
          equalTo("localhost")
        )
        context.assertThat(
          addresses.getJsonObject(0).getInteger("port"),
          equalTo(nodeAPort)
        )
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }

  @Test
  fun shouldListNetworkNotaries(context: TestContext) {
    val async = context.async()
    log.trace("calling get: https://localhost:$port/api/rest/network/notaries")
    client.getFuture(
      "/api/rest/network/notaries",
      headers = mapOf("Accept" to "application/json; charset=utf8")
        .addBearerToken(loginToken)
    )
      .compose { it.body<List<Party>>() }
      .onSuccess { nodes ->
        context.assertThat(nodes.size, equalTo(1))
        context.assertThat(
          nodes.first(),
          equalTo(driver!!.dsl.defaultNotaryIdentity)
        )
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }

  @Test
  fun shouldListFlows(context: TestContext) {
    val async = context.async()
    log.trace("calling get: https://localhost:$port/api/rest/cordapps/flows")
    client.getFuture(
      "/api/rest/cordapps/corda-core/flows",
      emptyMap<String, Any>().addBearerToken(loginToken)
    )
      .compose { it.body<List<String>>() }
      .onSuccess { flows ->
        context.assertEquals(
          0,
          flows.size
        ) // should not be exposing anything from corda's own flows
      }
      .compose {
        client.getFuture(
          "/api/rest/cordapps/corda-finance-workflows/flows",
          emptyMap<String, Any>().addBearerToken(loginToken)
        )
      }
      .compose { it.body<List<String>>() }
      .onSuccess { flows ->
        context.assertThat(flows, hasItem("net.corda.finance.flows.CashIssueFlow"))
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }

  @Test
  fun `should Start a CashIssueFlow`(context: TestContext) {
    val async = context.async()

    getNotary()
      .compose { notary ->
        val json = JsonObject()
          .put("notary", notary)
          .put(
            "amount",
            JsonObject(Json.encode(AMOUNT(10.00, Currency.getInstance("GBP"))))
          )
          .put("issuerBankPartyRef", JsonObject().put("bytes", "AABBCC"))
        val path =
          "/api/rest/cordapps/corda-finance-workflows/flows/net.corda.finance.flows.CashIssueFlow"
        log.trace("calling post: https://localhost:$port$path")
        val encodePrettily = json.encodePrettily()
        client.postFuture(
          path,
          mapOf(
            "Accept" to "application/json; charset=utf8",
            "Content-length" to "${encodePrettily.length}"
          ).addBearerToken(loginToken),
          body = encodePrettily
        )
      }
      .compose { it.body<JsonObject>() }
      .onSuccess { reply ->
        log.trace("reply:" + reply.encodePrettily())
        context.assertThat(reply, notNullValue())
        context.assertThat(reply.getJsonObject("stx"), notNullValue())
        context.assertThat(reply.getJsonObject("recipient"), notNullValue())

        val signedTransactionJson = reply.getJsonObject("stx").encodePrettily()
        log.trace(signedTransactionJson)

        //  todo round trip SignedTransaction
        // Failed to decode: Expected exactly 1 of {nodeSerializationEnv, driverSerializationEnv, contextSerializationEnv, inheritableContextSerializationEnv}
        withTestSerializationEnvIfNotSet {
          Json.decodeValue(signedTransactionJson, SignedTransaction::class.java)
        }
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }

  @Test
  fun `should Start a CashIssueFlow with ProgressTracker`(context: TestContext) {
    val async = context.async()

    val tracker =  client.getFuture("/api/rest/cordapps/progress-tracker",
      headers = mapOf("Accept" to "application/json; charset=utf8")
        .addBearerToken(loginToken)
    ).compose {
      context.assertThat(it.statusCode(), `is`(200), "expecting to find progress tracker")
      val future = Future.future<Buffer>()
      it.handler{buffer -> future.complete(buffer) }
      future
    }
    
    getNotary()
      .compose { notary ->
        val json = JsonObject()
          .put("notary", notary)
          .put("amount", JsonObject(Json.encode(AMOUNT(10.00, Currency.getInstance("GBP")))))
          .put("issuerBankPartyRef", JsonObject().put("bytes", "AABBCC"))
        val path = "/api/rest/cordapps/corda-finance-workflows/flows/net.corda.finance.flows.CashIssueFlow"
        log.info("calling post: https://localhost:$port$path")
        val encodePrettily = json.encodePrettily()
        client.postFuture(path,
          mapOf("Accept" to "application/json; charset=utf8",
            "Content-length" to "${encodePrettily.length}",
            "invocation-id" to "123")
            .addBearerToken(loginToken)
          ,
          body = encodePrettily)
      }
      .compose { it.body<JsonObject>()  }
      .compose { reply ->
        log.info("reply:" + reply.encodePrettily())
        context.assertThat(reply, notNullValue())
        context.assertThat(reply.getJsonObject("stx"), notNullValue())
        context.assertThat(reply.getJsonObject("recipient"), notNullValue())

        tracker
      }
      .map{ buffer ->
        log.info("progress tracker 1st reply:" + buffer.toString())
        val progress = Json.decodeValue(buffer.toString(), ProgressNotification::class.java)
        context.assertThat(progress.step, equalTo("Starting"), "expecting to find string step status")
        context.assertThat(progress.invocationId, equalTo("123"), "expecting to find invocation id")
        progress
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }
  
  @Test
  fun shouldReplyWithDecentErrorOnBadJson(context: TestContext) {
    val async = context.async()

    getNotary().map { jsonObject ->
      val notary = jsonObject

      val json = JsonObject()
        .put("notary", notary)
        .put(
          "amount",
          JsonObject(Json.encode(AMOUNT(10.00, Currency.getInstance("GBP"))))
        )
        .put("issuerBaaaaaankPartyRef", JsonObject().put("junk", "sdsa"))

      val path =
        "/api/rest/cordapps/corda-finance-workflows/flows/net.corda.finance.flows.CashIssueFlow"
      log.trace("calling post: https://localhost:$port$path")

      client.postFuture(
        path,
        headers = mapOf("Accept" to "application/json; charset=utf8")
          .addBearerToken(loginToken),
        body = json
      )
        .compose { it.body<String>() }
        .onSuccess { error("should have failed") }
        .catch {
          assertTrue("should have failed", it is HttpStatusException)
          val cause = it as HttpStatusException
          assertEquals(
            Response.Status.BAD_REQUEST.statusCode,
            cause.statusCode,
            cause.message
          )
          context.assertThat(cause.payload, containsString("issuerBaaaaaankPartyRef"))
          async.complete()
        }
        .catch(context::fail)
    }
  }

  @Test
  fun `should list cordapps`(context: TestContext) {
    val async = context.async()
    val path = "/api/rest/cordapps"
    client.getFuture(path, emptyMap<String, Any>().addBearerToken(loginToken))
      .compose { it.body<List<String>>() }
      .onSuccess { list ->
        context.assertTrue(list.contains("corda-core"))
        context.assertTrue(list.contains("corda-finance-contracts"))
        context.assertTrue(list.contains("corda-finance-workflows"))
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }

  private fun getNotary(): Future<JsonObject> {
    return client.getFuture(
      "/api/rest/network/notaries",
      headers = mapOf("Accept" to "application/json; charset=utf8")
        .addBearerToken(loginToken)
    )
      .compose { it.body<JsonArray>() }
      .map { nodes ->
        //   val nodes = Json.decodeValue(it, object : TypeReference<List<Party>>() {})
        nodes.getJsonObject(0)
      }
  }

  @Test
  fun `should query the vault`(context: TestContext) {
    val async = context.async()
    log.trace("calling get: https://localhost:${port}/api/rest/vault/vaultQuery")
    client.getFuture(
      "/api/rest/vault/vaultQuery",
      headers = mapOf("Accept" to "application/json; charset=utf8")
        .addBearerToken(loginToken)
    )
      .compose { it.body<JsonObject>() }
      .onSuccess { nodes ->
        vertxAssertThat(context, nodes, notNullValue())
      }
      .onSuccess { async.complete() }
      .catch(context::fail)
  }

  @Test
  fun `should query the vault for a specific type`(context: TestContext) {
    val async = context.async()

    log.trace("calling get: https://localhost:${port}/api/rest/vault/vaultQuery?contract-state-type=" + ContractState::class.java.name)
    client.getFuture(
      "/api/rest/vault/vaultQuery", headers = mapOf(
      "Accept" to "application/json; charset=utf8",
      "contract-state-type" to ContractState::class.java.name
    ).addBearerToken(loginToken)
    )
      .compose { it.body<JsonObject>() }
      .onSuccess { nodes ->
        vertxAssertThat(context, nodes, notNullValue())
        async.complete()
      }
      .catch(context::fail)
  }

  @Test
  fun `should query the vault by type`(context: TestContext) {
    val async = context.async()
    val json = """
{
  "criteria" : {
    "@class" : ".QueryCriteria${'$'}VaultQueryCriteria",
    "status" : "UNCONSUMED",
    "contractStateTypes" : null,
    "stateRefs" : null,
    "notary" : null,
    "softLockingCondition" : null,
    "timeCondition" : {
      "type" : "RECORDED",
      "predicate" : {
        "@class" : ".ColumnPredicate${'$'}Between",
        "rightFromLiteral" : "2019-09-15T12:58:23.283Z",
        "rightToLiteral" : "2019-10-15T12:58:23.283Z"
      }
    },
    "relevancyStatus" : "ALL",
    "constraintTypes" : [ ],
    "constraints" : [ ],
    "participants" : null
  },
  "paging" : {
    "pageNumber" : -1,
    "pageSize" : 200
  },
  "sorting" : {
    "columns" : [ ]
  },
  "contractStateType" : "net.corda.core.contracts.ContractState"
}
"""
    log.trace("calling post: https://localhost:${port}/api/rest/vault/vaultQueryBy")
    client.postFuture(
      "/api/rest/vault/vaultQueryBy",
      body = json,
      headers = mapOf("Accept" to "application/json; charset=utf8")
        .addBearerToken(loginToken)
    )
      .compose { it.body<JsonObject>() }
      .onSuccess { nodes ->
        vertxAssertThat(context, nodes, notNullValue())
//        println(nodes.encodePrettily())
        async.complete()
      }
      .catch(context::fail)
  }

  @Test
  fun `should serialize various query`() {
    val generalCriteria = VaultQueryCriteria(Vault.StateStatus.ALL)
    val currencyIndex = CashSchemaV1.PersistentCashState::currency.equal("GBP")
    val quantityIndex = CashSchemaV1.PersistentCashState::pennies.greaterThanOrEqual(0L)

    val customCriteria2 = VaultCustomQueryCriteria(quantityIndex)
    val customCriteria1 = VaultCustomQueryCriteria(currencyIndex)

    val criteria = generalCriteria
      .and(customCriteria1)
      .and(customCriteria2)

    VaultQuery(criteria, contractStateType = Cash.State::class.java)

//    val json = Json.encodePrettily(query)
//    println(json)
  }

  @Test
  fun `should query the vault by various criteria`(context: TestContext) {
    val async = context.async()
    val json = """{
  "criteria" : {
    "@class" : ".QueryCriteria${'$'}AndComposition",
    "a" : {
      "@class" : ".QueryCriteria${'$'}AndComposition",
      "a" : {
        "@class" : ".QueryCriteria${'$'}VaultQueryCriteria",
        "status" : "ALL"
      },
      "b" : {
        "@class" : ".QueryCriteria${'$'}VaultCustomQueryCriteria",
        "expression" : {
          "@class" : ".CriteriaExpression${'$'}ColumnPredicateExpression",
          "column" : {
            "name" : "currency",
            "declaringClass" : "net.corda.finance.schemas.CashSchemaV1${'$'}PersistentCashState"
          },
          "predicate" : {
            "@class" : ".ColumnPredicate${'$'}EqualityComparison",
            "operator" : "EQUAL",
            "rightLiteral" : "GBP"
          }
        },
        "status" : "UNCONSUMED",
        "relevancyStatus" : "ALL"
      }
    },
    "b" : {
      "@class" : ".QueryCriteria${'$'}VaultCustomQueryCriteria",
      "expression" : {
        "@class" : ".CriteriaExpression${'$'}ColumnPredicateExpression",
        "column" : {
          "name" : "pennies",
          "declaringClass" : "net.corda.finance.schemas.CashSchemaV1${'$'}PersistentCashState"
        },
        "predicate" : {
          "@class" : ".ColumnPredicate${'$'}BinaryComparison",
          "operator" : "GREATER_THAN_OR_EQUAL",
          "rightLiteral" : 0
        }
      },
      "status" : "UNCONSUMED",
      "relevancyStatus" : "ALL"
    }
  },
  "contractStateType" : "net.corda.finance.contracts.asset.Cash${'$'}State"
}"""

    log.trace("calling post: https://localhost:${port}/api/rest/vault/vaultQueryBy")
    client.postFuture(
      "/api/rest/vault/vaultQueryBy",
      body = json,
      headers = mapOf("Accept" to "application/json; charset=utf8")
        .addBearerToken(loginToken)
    )
      .compose { it.body<JsonObject>() }
      .onSuccess { nodes ->
        vertxAssertThat(context, nodes, notNullValue())
        async.complete()
      }
      .catch(context::fail)
  }

  @Test
  @Ignore
  fun `should issue obligation`(context: TestContext) {
    val async = context.async()
    getNotary().map {
      val json = """
{
  "amount": {
    "quantity": 100,
    "displayTokenSize": 0.01,
    "token": "GBP"
  },
  "lender": {
    "name": "O=PartyB, L=New York, C=US",
    "owningKey": "GfHq2tTVk9z4eXgyWBgg9GY6LaCcQjjaSFVwKkJ5j1VyaU5nWjEijR28xxay"
  },
  "anonymous": false
}      """.trimIndent()

      val path =
        "/api/rest/cordapps/kotlin-source/flows/net.corda.examples.obligation.flows.IssueObligation\$Initiator"
      log.trace("calling post: https://localhost:$port$path")

      client.postFuture(
        path,
        body = json,
        headers = mapOf("Accept" to "application/json; charset=utf8")
          .addBearerToken(loginToken)
      )
        .compose { it.body<JsonObject>() }
        .onSuccess { reply ->
          log.trace("reply:" + reply.encodePrettily())
          context.assertThat(reply, notNullValue())
          context.assertThat(reply.getJsonObject("stx"), notNullValue())
          context.assertThat(reply.getJsonObject("recipient"), notNullValue())
          async.complete()
        }
        .catch(context::fail)
    }
  }
}
