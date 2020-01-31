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
package io.bluebank.braid.server

import io.bluebank.braid.core.http.HttpServerConfig
import io.bluebank.braid.core.http.end
import io.bluebank.braid.core.jsonrpc.JsonRPCMounter
import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import io.bluebank.braid.core.jsonrpc.JsonRPCResponse
import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.meta.ServiceDescriptor
import io.bluebank.braid.core.meta.defaultServiceEndpoint
import io.bluebank.braid.core.reflection.serviceName
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
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.handler.sockjs.SockJSSocket
import javax.ws.rs.core.Response

data class BraidConfig(
  val port: Int,
  val authProvider: AuthProvider?,
  val rootPath: String = "/api/",
  val httpServerOptions: HttpServerOptions = HttpServerConfig.defaultServerOptions(),
  val threadPoolSize: Int = 1
)

// later we may adapt this to add support for the REGISTERED_HANDLERS in CordaSockJSHandler
// and instead of being a class this could be a function which returns a Map<String, ServiceExecutor>
class ServiceMap(services: List<Any>) {

  val services: Map<String, ServiceExecutor> = services.associateBy(
    { it.javaClass.serviceName() },
    { ConcreteServiceExecutor(it) }
  )
}

class BraidSockJSHandler private constructor(
  private val vertx: Vertx,
  private val serviceMap: ServiceMap,
  private val config: BraidConfig
) : Handler<SockJSSocket> {

  companion object {
    private val log = loggerFor<BraidSockJSHandler>()

    fun setupSockJSHandler(
      router: Router,
      vertx: Vertx,
      serviceMap: ServiceMap,
      config: BraidConfig
    ) {
      val sockJSHandler = SockJSHandler.create(vertx)
      val handler = BraidSockJSHandler(vertx, serviceMap, config)
      sockJSHandler.socketHandler(handler)
      val protocol = if (config.httpServerOptions.isSsl) "https" else "http"

      // mount each service
      println("Mounting braid services...")
      log.info("root API mount for braid: ${config.rootPath}")
      serviceMap.services.forEach {
        val serviceName = it.key
        val endpoint = defaultServiceEndpoint(config.rootPath, serviceName) + "/*"
        log.info("mounting braid service $serviceName to $protocol://localhost:${config.port}$endpoint")
        router.route(endpoint).handler(sockJSHandler)
      }
      router.get(config.rootPath).handler {
        val services = serviceMap.services.keys
        it.end(ServiceDescriptor.createServiceDescriptors(config.rootPath, services))
      }
      router.get("${config.rootPath}:serviceName").handler {
        val serviceName = it.pathParam("serviceName")
        val serviceDoc = handler.getDocumentation()[serviceName]
        if (serviceDoc != null) {
          it.end(serviceDoc)
        } else {
          it.end(
            RuntimeException("could not find service $serviceName"),
            Response.Status.BAD_REQUEST.statusCode
          )
        }
      }
    }
  }

  private val pathRegEx = Regex("${config.rootPath.replace("/", "\\/")}([^\\/]+).*")

  override fun handle(socket: SockJSSocket) {
    val serviceName = pathRegEx.matchEntire(socket.uri())?.groupValues?.get(1) ?: ""
    val service = serviceMap.services[serviceName]
    if (service != null) {
      handleKnownService(socket, config.authProvider, service)
    } else {
      handleUnknownService(socket, serviceName)
    }
  }

  fun getDocumentation(): Map<String, List<MethodDescriptor>> {
    return serviceMap.services.map {
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
  services: ServiceMap,
  config: BraidConfig
): Router {
  BraidSockJSHandler.setupSockJSHandler(this, vertx, services, config)
  return this
}