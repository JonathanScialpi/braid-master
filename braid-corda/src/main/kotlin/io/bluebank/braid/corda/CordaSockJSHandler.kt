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

import io.bluebank.braid.corda.services.CordaFlowServiceExecutor
import io.bluebank.braid.corda.services.SimpleNetworkMapServiceImpl
import io.bluebank.braid.corda.services.adapters.toCordaServicesAdapter
import io.bluebank.braid.core.http.end
import io.bluebank.braid.core.jsonrpc.JsonRPCMounter
import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import io.bluebank.braid.core.jsonrpc.JsonRPCResponse
import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.meta.ServiceDescriptor
import io.bluebank.braid.core.meta.defaultServiceEndpoint
import io.bluebank.braid.core.security.AuthenticatedSocket
import io.bluebank.braid.core.service.ConcreteServiceExecutor
import io.bluebank.braid.core.service.MethodDescriptor
import io.bluebank.braid.core.service.ServiceExecutor
import io.bluebank.braid.core.socket.SockJSSocketWrapper
import io.bluebank.braid.core.socket.Socket
import io.bluebank.braid.core.socket.TypedSocket
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.handler.sockjs.SockJSSocket
import net.corda.core.node.AppServiceHub
import javax.ws.rs.core.Response

class CordaSockJSHandler private constructor(
  private val vertx: Vertx,
  serviceHub: AppServiceHub,
  private val config: BraidConfig
) : Handler<SockJSSocket> {

  companion object {
    private val log = loggerFor<CordaSockJSHandler>()
    private val REGISTERED_HANDLERS = mapOf(
      "network" to this::createNetworkMapService,
      "flows" to this::createFlowService
    )

    fun setupSockJSHandler(
      router: Router,
      vertx: Vertx,
      serviceHub: AppServiceHub,
      config: BraidConfig
    ) {
      val sockJSHandler = SockJSHandler.create(vertx)
      val handler = CordaSockJSHandler(vertx, serviceHub, config)
      sockJSHandler.socketHandler(handler)
      val protocol = if (config.httpServerOptions.isSsl) "https" else "http"

      // mount each service
      println("Mounting braid services...")
      log.info("root API mount for braid: ${config.rootPath}")
      registerCoreServices(protocol, config, router, sockJSHandler)
      registerCustomService(config, protocol, router, sockJSHandler)
      router.get(config.rootPath).handler {
        val services = REGISTERED_HANDLERS.keys + config.services.keys
        it.end(ServiceDescriptor.createServiceDescriptors(config.rootPath, services))
      }
      router.get("${config.rootPath}:serviceName").handler {
        val serviceName = it.pathParam("serviceName")
        val serviceDoc = handler.getDocumentation()[serviceName]
        if (serviceDoc != null) {
          it.end(serviceDoc)
        } else {
          it.end(RuntimeException("could not find service $serviceName"), Response.Status.BAD_REQUEST.statusCode)
        }
      }
    }

    private fun registerCustomService(
      config: BraidConfig,
      protocol: String,
      router: Router,
      sockJSHandler: SockJSHandler
    ) {
      config.services.forEach {
        mountServiceName(it.key, protocol, config, router, sockJSHandler)
      }
    }

    private fun registerCoreServices(
      protocol: String,
      config: BraidConfig,
      router: Router,
      sockJSHandler: SockJSHandler
    ) {
      REGISTERED_HANDLERS.forEach {
        mountServiceName(it.key, protocol, config, router, sockJSHandler)
      }
    }

    private fun mountServiceName(
      serviceName: String,
      protocol: String,
      config: BraidConfig,
      router: Router,
      sockJSHandler: SockJSHandler
    ) {
      val endpoint = defaultServiceEndpoint(config.rootPath, serviceName) + "/*"
      log.info("mounting braid service $serviceName to $protocol://localhost:${config.port}$endpoint")
      router.route(endpoint).handler(sockJSHandler)
    }

    private fun createNetworkMapService(
      services: AppServiceHub,
      @Suppress("UNUSED_PARAMETER") config: BraidConfig
    ): ServiceExecutor =
      ConcreteServiceExecutor(SimpleNetworkMapServiceImpl(services.toCordaServicesAdapter()))

    private fun createFlowService(
      services: AppServiceHub,
      config: BraidConfig
    ): ServiceExecutor =
      CordaFlowServiceExecutor(services.toCordaServicesAdapter(), config)
  }

  private val authProvider = config.authConstructor?.invoke(vertx)
  private val serviceMap =
    REGISTERED_HANDLERS.map { it.key to it.value(serviceHub, config) }.toMap() +
      config.services.map { it.key to ConcreteServiceExecutor(it.value) }.toMap()
  private val pathRegEx = Regex("${config.rootPath.replace("/", "\\/")}([^\\/]+).*")

  override fun handle(socket: SockJSSocket) {
    val serviceName = pathRegEx.matchEntire(socket.uri())?.groupValues?.get(1) ?: ""
    val service = serviceMap[serviceName]
    if (service != null) {
      handleKnownService(socket, authProvider, service)
    } else {
      handleUnknownService(socket, serviceName)
    }
  }

  fun getDocumentation(): Map<String, List<MethodDescriptor>> {
    return serviceMap.map {
      it.key to it.value.getStubs()
    }.toMap()
  }

  private fun handleUnknownService(socket: SockJSSocket, serviceName: String) {
    socket.write("cannot find service $serviceName")
    socket.close()
  }

  private fun handleKnownService(
    socket: SockJSSocket,
    authProvider: AuthProvider?,
    service: ServiceExecutor
  ) {
    val sockWrapper = createSocketAdapter(socket, authProvider)
    val rpcSocket = TypedSocket.create<JsonRPCRequest, JsonRPCResponse>()
    sockWrapper.addListener(rpcSocket)
    val mount = JsonRPCMounter(service, vertx)
    rpcSocket.addListener(mount)
  }

  private fun createSocketAdapter(
    socket: SockJSSocket,
    authProvider: AuthProvider?
  ): Socket<Buffer, Buffer> {
    val sockJSWrapper = SockJSSocketWrapper.create(socket, vertx, config.threadPoolSize)
    return if (authProvider == null) {
      sockJSWrapper
    } else {
      // we tag on the authenticator on the pipeline and return that
      val authenticatedSocket = AuthenticatedSocket.create(authProvider)
      sockJSWrapper.addListener(authenticatedSocket)
      authenticatedSocket
    }
  }
}

fun Router.setupSockJSHandler(
  vertx: Vertx,
  services: AppServiceHub,
  config: BraidConfig
): Router {
  CordaSockJSHandler.setupSockJSHandler(this, vertx, services, config)
  return this
}