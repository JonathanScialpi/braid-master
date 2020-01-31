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
package io.bluebank.braid.corda.server.rpc

import io.bluebank.braid.corda.rest.RestMounter
import io.bluebank.braid.corda.server.BraidAuth
import io.bluebank.braid.core.logging.loggerFor
import io.swagger.v3.oas.annotations.media.Schema
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.ext.auth.User
import io.vertx.ext.web.handler.impl.HttpStatusException
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

// todo manage recover and reconnection etc probably with Future class

data class LoginRequest(
  @Schema(description = "user name", example = "sa")
  val user: String,
  @Schema(description = "password", example = "admin")
  val password: String
)

fun connect(
  userName: String,
  password: String,
  nodeAddress: NetworkHostAndPort,
  log: Logger
): CordaRPCOps {
  log.info("Attempting to connect to Corda $nodeAddress as username $userName")
  try {
    val client =
      CordaRPCClient(
        nodeAddress,
        classLoader = Thread.currentThread().contextClassLoader
      )
    val connection = client.start(userName, password)
    log.info("Connection established as username $userName")
    return connection.proxy
  } catch (err: Throwable) {
    log.info("Connection failed", err)
    throw err
  }
}

interface RPCConnections {
  fun getConnection(user: User?): CordaRPCOps
  fun addLoginPath(restMounter: RestMounter)
}

// called when braid itself logs in with configured credentials, users are unauthenticated
class RPCConnectionsShared(
  private val nodeAddress: NetworkHostAndPort,
  private val userName: String,
  private val password: String
) : RPCConnections {

  private val log = loggerFor<RPCConnectionsShared>()
  // a single, shared, just-in-time connection -- instead of the per-user
  // `connections = ConcurrentHashMap` which is implemented by RPCConnectionsAuth
  private val connection: CordaRPCOps by lazy {
    connect(userName, password, nodeAddress, log)
  }

  override fun getConnection(user: User?): CordaRPCOps {
    return connection
  }

  override fun addLoginPath(restMounter: RestMounter) {} // no login function
}

// called when users log in with their own credentials
class RPCConnectionsAuth(
  private val nodeAddress: NetworkHostAndPort,
  private val vertx: Vertx,
  private val braidAuth: BraidAuth
) : RPCConnections {

  private data class Connected(val connection: CordaRPCOps, val password: String)

  private val log = loggerFor<RPCConnectionsAuth>()
  private val connections = ConcurrentHashMap<String, Connected>()

  // Bind this::login method as the handler of the `/login` path.
  override fun addLoginPath(restMounter: RestMounter) {
    restMounter.post("/login", this::login)
  }

  // Caution: this method may be called by multiple event loop threads simultaneously
  // if in future this one RPCConnectionsImpl instance is shared to multiple verticles.
  // Also, login may take a while, so this method returns an asynch future.
  @Suppress("MemberVisibilityCanBePrivate")
  fun login(request: LoginRequest): Future<String> {
    val (userName, password) = request
    val result = Future.future<String>()

    // if already connected then can return immediately without executeBlocking
    val connected = connections[userName]
    if (connected != null) {
      loginCheckPassword(request, connected, result) // result.complete or result.fail
      return result;
    }

    val ordered = false // i.e. worker threads can execute in parallel not serially
    vertx.executeBlocking<Connected>({
      log.info("login executeBlocking username:$userName")
      val found = connections.computeIfAbsent(userName) {
        // then attempt to make the network connection
        // this may take a while and throw an exception on failure or if password is wrong
        val connection = connect(userName, password, nodeAddress, log)
        Connected(connection, password)
      }
      log.info("login connection found")
      it.complete(found)
    }, ordered, {
      if (it.failed()) {
        log.info("login failed", it.cause())
        loginFail(result)
      } else {
        loginCheckPassword(request, it.result(), result)
      }
    })

    return result
  }

  // Get the appropriate already-created RPC connection, for any already-logged-in user.
  override fun getConnection(user: User?): CordaRPCOps {
    if (user == null)
      error("expected a user identity")
    val userName = braidAuth.getUserName(user)
    val connected = connections[userName]
      ?: error("expected connection for this username")
    return connected.connection
  }

  private fun loginCheckPassword(
    request: LoginRequest,
    connected: Connected,
    result: Future<String>
  ) {
    val isPasswordOk = request.password == connected.password;
    log.info("loginCheckPassword username:${request.user}, isPasswordOk:$isPasswordOk")
    if (!isPasswordOk) {
      loginFail(result)
    } else {
      // login succeeded
      val token = braidAuth.generateToken(request.user)
      result.complete(token)
    }
  }

  private fun loginFail(result: Future<String>) {
    // discard the server-side exception cause and instead return HTTP 403
    result.fail(HttpStatusException(403, "Login Failed"))
  }
}