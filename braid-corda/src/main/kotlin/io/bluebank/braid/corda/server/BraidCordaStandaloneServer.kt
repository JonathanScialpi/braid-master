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

import io.bluebank.braid.corda.BraidConfig
import io.bluebank.braid.corda.BraidCordaJacksonSwaggerInit
import io.bluebank.braid.corda.rest.AuthSchema
import io.bluebank.braid.corda.rest.RestConfig
import io.bluebank.braid.corda.rest.RestMounter
import io.bluebank.braid.corda.server.flow.FlowInitiator
import io.bluebank.braid.corda.server.progress.TrackerHandler
import io.bluebank.braid.corda.server.rpc.RPCConnections
import io.bluebank.braid.corda.server.rpc.RPCConnectionsAuth
import io.bluebank.braid.corda.server.rpc.RPCConnectionsShared
import io.bluebank.braid.corda.services.CordaServicesAdapter
import io.bluebank.braid.corda.services.RestNetworkMapService
import io.bluebank.braid.corda.services.adapters.RPCFactoryCordaServicesAdapter
import io.bluebank.braid.corda.services.adapters.toCordaServicesAdapter
import io.bluebank.braid.corda.services.vault.VaultService
import io.bluebank.braid.core.http.HttpServerConfig
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.auth.User
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction1

open class BraidCordaStandaloneServer(
  val port: Int = 8080,
  val userName: String = "",
  val password: String = "",
  val nodeAddress: NetworkHostAndPort = NetworkHostAndPort("localhost", 8080),
  val openApiVersion: Int = 3,
  val vertx: Vertx = Vertx.vertx(),
  val httpServerOptions: HttpServerOptions = HttpServerConfig.defaultServerOptions()
) {

  companion object {
    private val log = loggerFor<BraidCordaStandaloneServer>()

    // all share the same BraidAuth instance if one is needed at all
    private val braidAuth by lazy { BraidAuth() }

    init {
      BraidCordaJacksonSwaggerInit.init()
    }
  }

  /**
   * There are two scenarios supported:
   *
   * 1. Users login,
   *    Braid uses the user's credentials to create a separate RPC connection per user,
   *    and the `User` object is used to get the right RPC connection for a logged-in user
   *
   * 2. Users don't login,
   *    Braid has configured credentials and creates a single RPC connection to be shared,
   *    and the `User?` object is null and ignored
   *
   * The following interface and its two subclasses distinguishes these two scenarios.
   */
  private interface Who {

    // create not get, because in future startServer might be called multiple times
    // todo which class will contain the Adapters instance and how any there should be?
    // perhaps only one Adapters instance in the process to be shared by all verticles?
    fun createAdapters(): Adapters

    // this may or may not call withAuthConstructor
    fun withWho(braidConfig: BraidConfig): BraidConfig

    val authSchema: AuthSchema
    val isAuth: Boolean
    val authConstructor: ((Vertx) -> AuthProvider)?
  }

  /**
   * This could have been just a function to convert an RPC connection to a adapter
   * because that conversion/construction is very cheap.
   * But this is a class, because we wanted to give it some state,
   * to cache the instantiated adapters, to avoid working the garbage collector.
   */
  private abstract class Adapters(val rpc: RPCConnections) {

    abstract fun getCordaServicesAdapter(user: User?): CordaServicesAdapter

    protected fun createCordaServicesAdapter(cordaRPCOps: CordaRPCOps): CordaServicesAdapter {
      val delegate = cordaRPCOps.toCordaServicesAdapter()
      return RPCFactoryCordaServicesAdapter(delegate)
    }
  }

  private fun BraidConfig.withWho(who: Who): BraidConfig = who.withWho(this)

  /**
   * Use this when users are unauthenticated and use a single shared connection
   */
  private class WhoShared(
    private val nodeAddress: NetworkHostAndPort,
    private val userName: String,
    private val password: String
  ) : Who {

    class MyAdapters(rpc: RPCConnections) : Adapters(rpc) {
      // a single instance
      private val servicesAdapter: CordaServicesAdapter by lazy {
        // use null User to instantiate the connection
        createCordaServicesAdapter(rpc.getConnection(null))
      }

      override fun getCordaServicesAdapter(user: User?): CordaServicesAdapter {
        // assume the user parameter is null and ignore it
        return servicesAdapter
      }
    }

    override fun createAdapters(): Adapters {
      val rpc: RPCConnections = RPCConnectionsShared(nodeAddress, userName, password)
      return MyAdapters(rpc)
    }

    override fun withWho(braidConfig: BraidConfig): BraidConfig {
      // nothing to do, i.e. don't call braidConfig.withAuth
      return braidConfig
    }

    override val authSchema: AuthSchema
      get() = AuthSchema.None
    override val isAuth: Boolean
      get() = false
    override val authConstructor: ((Vertx) -> AuthProvider)?
      get() = null
  }

  /**
   * Use this when users login and each userName has its own RPC connection to
   */
  private class WhoAuth(
    private val nodeAddress: NetworkHostAndPort,
    private val vertx: Vertx
  ) : Who {

    class MyAdapters(rpc: RPCConnections) : Adapters(rpc) {

      // this need not be concurrent now, but in future there could be multiple verticles?
      private val serviceAdapters = ConcurrentHashMap<String?, CordaServicesAdapter>()

      override fun getCordaServicesAdapter(user: User?): CordaServicesAdapter {
        if (user == null) {
          // the framework shouldn't even have let this get this far
          // because of the paths being declared within RestMounter.protected
          error("User instance is null -- user not logged in?")
        }
        val userName = braidAuth.getUserName(user)
        // get the cached adapter or compute it now -- it's cached for reuse after being
        // computed even though creating one is cheap, just to reduce garbage collection
        return serviceAdapters.computeIfAbsent(userName) {
          // the RPC connection should already exist, this now creates its adapter
          createCordaServicesAdapter(rpc.getConnection(user))
        }
      }
    }

    override fun createAdapters(): Adapters {
      val rpc: RPCConnections = RPCConnectionsAuth(nodeAddress, vertx, braidAuth)
      return MyAdapters(rpc)
    }

    override fun withWho(braidConfig: BraidConfig): BraidConfig {
      return braidConfig.withAuthConstructor(braidAuth::authConstructor)
    }

    override val authSchema: AuthSchema
      get() = AuthSchema.Token
    override val isAuth: Boolean
      get() = true
    override val authConstructor: ((Vertx) -> AuthProvider)?
      get() = braidAuth::authConstructor
  }

  // decide which authentication strategy we're using
  private val who: Who = if (userName.isNullOrBlank()) WhoAuth(nodeAddress, vertx)
  else WhoShared(nodeAddress, userName, password)

  // this is so that BraidDocsMain can call .withAuth like BraidVerticle does
  val authConstructor: ((Vertx) -> AuthProvider)?
    get() = who.authConstructor

  fun startServer(): Future<String> {
    log.info("Starting Braid on port: $port")
    val result = Future.future<String>()
    BraidConfig()
      .withPort(port)
      .withHttpServerOptions(httpServerOptions)
      .withRestConfig(createRestConfig())
      .withWho(who)
      .withRestConfig(createRestConfig(openApiVersion))
      .withVertx(vertx)
      .bootstrapBraid(null, result)
    //addShutdownHook {  }
    return result
  }

  fun createRestConfig(openApiVersion: Int = 2): RestConfig {
    val classLoader = Thread.currentThread().contextClassLoader
    val cordappsScanner = CordaClasses(classLoader)

    val adapters = who.createAdapters()

    val networkService = RestNetworkMapService(adapters::getCordaServicesAdapter)
    val vaultService = VaultService(adapters.rpc::getConnection)

    return RestConfig()
      .withOpenApiVersion(openApiVersion)
      .withAuthSchema(who.authSchema)
      .withPaths {
        cordappsScanner.cordaSerializableClasses.forEach {
          this.docsHandler.addType(it.java)
        }
        protected {
          group("network") {
            get("/network/nodes", networkService::nodes)
            get("/network/nodes/self", networkService::myNodeInfo)
            get("/network/notaries", networkService::notaries)
          }
          group("vault") {
            get("/vault/vaultQuery", vaultService::vaultQuery)
            post("/vault/vaultQueryBy", vaultService::vaultQueryBy)
          }
          group("cordapps") {
            get("/cordapps", cordappsScanner::cordapps)
            get("/cordapps/progress-tracker", TrackerHandler(vertx.eventBus())::handle)
            get("/cordapps/:cordapp/flows", cordappsScanner::flowsForCordapp)
            try {
              cordappsScanner.flowClassesByCordapp.forEach { (cordapp, flowClass) ->
                addFlow(cordapp, flowClass, adapters::getCordaServicesAdapter)
              }
            } catch (e: Throwable) {
              log.error("failed to register flows", e)
            }
          }
        }
        unprotected {
          adapters.rpc.addLoginPath(this)
        }
      }
  }

  private fun RestMounter.addFlow(cordapp: String, flowClass: KClass<out Any>, cordaServicesAdapter: KFunction1<User?, CordaServicesAdapter>) {
    try {
      val path = "/cordapps/$cordapp/flows/${flowClass.java.name}"
      log.info("registering: $path")
      post(path, FlowInitiator(cordaServicesAdapter, vertx.eventBus(), who.isAuth).getInitiator(flowClass))
    } catch (e: Throwable) {
      log.warn("unable to register flow:${flowClass.java.name}", e);
    }
  }
}
