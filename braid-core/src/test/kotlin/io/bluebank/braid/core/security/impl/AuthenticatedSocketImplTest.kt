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
package io.bluebank.braid.core.security.impl

import io.bluebank.braid.core.json.BraidJacksonInit
import io.bluebank.braid.core.jsonrpc.JsonRPCErrorResponse
import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import io.bluebank.braid.core.jsonrpc.JsonRPCResultResponse
import io.bluebank.braid.core.jsonrpc.MockSocket
import io.bluebank.braid.core.security.AuthenticatedSocket
import io.bluebank.braid.core.security.AuthenticatedSocket.Companion.LOGIN_METHOD
import io.bluebank.braid.core.security.AuthenticatedSocket.Companion.LOGOUT_METHOD
import io.bluebank.braid.core.security.AuthenticatedSocket.Companion.MSG_FAILED
import io.bluebank.braid.core.security.AuthenticatedSocket.Companion.MSG_PARAMETER_ERROR
import io.bluebank.braid.core.security.JWTUtils
import io.bluebank.braid.core.socket.Socket
import io.bluebank.braid.core.socket.SocketListener
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.jwt.JWTOptions
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.kotlin.ext.auth.keyStoreOptionsOf
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(VertxUnitRunner::class)
class AuthenticatedSocketImplTest {

  companion object {
    init {
      BraidJacksonInit.init()
    }
  }

  private val vertx = Vertx.vertx()
  private val jwtSecret = "secret"
  private val tempJKS = File.createTempFile("temp-", ".jceks")!!.also { file ->
    val ks = JWTUtils.createSimpleJWTKeyStore(jwtSecret)
    FileOutputStream(file.absoluteFile).use { outputStream ->
      ks.store(outputStream, jwtSecret.toCharArray())
      outputStream.flush()
    }
  }
  private val jwtAuth: JWTAuth = JWTAuth.create(
    vertx,
    JWTAuthOptions().setKeyStore(
      keyStoreOptionsOf(
        jwtSecret,
        tempJKS.absolutePath,
        "jceks"
      )
    )
  )

  private val authSocket = AuthenticatedSocket.create(jwtAuth)

  @After
  fun after(context: TestContext) {
    val async = context.async()
    vertx.close {
      async.complete()
    }
  }

  @Test
  fun `that socket authenticates with credentials payload and we can logout`() {
    val userField = "user"
    val user = "fuzz"
    val predecessor = MockSocket<Buffer, Buffer>()
    predecessor.addResponseListener {
      val json = JsonObject(it)
      assertTrue(
        json.containsKey("result") && json.getString("result") == "OK",
        "has result field"
      )
      assertTrue(json.containsKey("id"), "has id field")
    }
    predecessor.addListener(authSocket)
    val token = jwtAuth.generateToken(JsonObject().put(userField, user), JWTOptions())
    val loginRequest = JsonRPCRequest(
      id = 1,
      method = LOGIN_METHOD,
      params = listOf(JWTLoginPayload(token))
    )
    authSocket.onData(predecessor, Json.encodeToBuffer(loginRequest))
    assertNotNull(authSocket.user(), "that we have an authenticated user")
    val uid = authSocket.user()!!.principal().getString("user")
    assertEquals(user, uid, "that user id is $user")

    val logoutRequest = JsonRPCRequest(
      id = 2,
      method = LOGOUT_METHOD,
      params = null
    )
    authSocket.onData(predecessor, Json.encodeToBuffer(logoutRequest))
    assertNull(authSocket.user(), "that we have logged out")

    assertEquals(2, predecessor.writeCount)
  }

  @Test
  fun `that socket fails if credentials don't pass`() {
    val predecessor = MockSocket<Buffer, Buffer>()
    predecessor.addResponseListener {
      val response = Json.decodeValue(it, JsonRPCErrorResponse::class.java)
      assertEquals(MSG_FAILED, response.error.message)
    }
    predecessor.addListener(authSocket)
    val request = JsonRPCRequest(
      id = 1,
      method = LOGIN_METHOD,
      params = listOf(JWTLoginPayload("dodgy token"))
    )
    authSocket.onData(predecessor, Json.encodeToBuffer(request))
    assertEquals(1, predecessor.writeCount)
  }

  @Test
  fun `that socket fails if parameters not correct`() {
    val predecessor = MockSocket<Buffer, Buffer>()
    predecessor.addResponseListener {
      val response = Json.decodeValue(it, JsonRPCErrorResponse::class.java)
      assertEquals(MSG_PARAMETER_ERROR, response.error.message)
    }
    predecessor.addListener(authSocket)
    val requests = listOf(
      JsonRPCRequest(id = 1, method = LOGIN_METHOD, params = null),
      JsonRPCRequest(id = 1, method = LOGIN_METHOD, params = "bad param"),
      JsonRPCRequest(id = 1, method = LOGIN_METHOD, params = listOf<String>()),
      JsonRPCRequest(id = 1, method = LOGIN_METHOD, params = listOf("bad param"))
    )
    requests.map { Json.encodeToBuffer(it) }
      .forEach { authSocket.onData(predecessor, it) }

    assertEquals(requests.size, predecessor.writeCount)
  }

  @Test
  fun `that an authenticated socket passes on the request`() {
    val predecessor = MockSocket<Buffer, Buffer>()
    predecessor.addResponseListener {
      val jo = JsonObject(it)
      when (jo.getInteger("id")) {
        4 -> Json.decodeValue(
          it,
          JsonRPCErrorResponse::class.java
        ) // invocation 4 should be unsuccessful because logged out
        else -> Json.decodeValue(it, JsonRPCResultResponse::class.java)
      }
    }

    predecessor.addListener(authSocket)
    var hasEnded = false
    authSocket.addListener(object : SocketListener<Buffer, Buffer> {
      override fun onRegister(socket: Socket<Buffer, Buffer>) {
      }

      override fun onData(socket: Socket<Buffer, Buffer>, item: Buffer) {
        val request = Json.decodeValue(item, JsonRPCRequest::class.java)
        socket.write(
          Json.encodeToBuffer(
            JsonRPCResultResponse(
              id = request.id,
              result = "OK"
            )
          )
        )
      }

      override fun onEnd(socket: Socket<Buffer, Buffer>) {
        hasEnded = true
      }
    })

    fun AuthenticatedSocket.dataHandler(request: JsonRPCRequest) {
      this.onData(predecessor, Json.encodeToBuffer(request))
    }

    val token = jwtAuth.generateToken(JsonObject(), JWTOptions())

    authSocket.dataHandler(
      JsonRPCRequest(
        id = 1,
        method = LOGIN_METHOD,
        params = listOf(JWTLoginPayload(token))
      )
    )
    authSocket.dataHandler(JsonRPCRequest(id = 2, method = "someMethod", params = null))
    authSocket.dataHandler(JsonRPCRequest(id = 3, method = LOGOUT_METHOD, params = null))
    authSocket.dataHandler(JsonRPCRequest(id = 4, method = "someMethod", params = null))
    assertEquals(4, predecessor.writeCount)
    predecessor.end()
    assertTrue(hasEnded)
  }

  @Test
  fun `that when handling a malformed login request and the network has failed that no exception in thrown`() {
    val predecessor = MockSocket<Buffer, Buffer>()
    predecessor.addResponseListener {
      error("network failure")
    }
    predecessor.addListener(authSocket)
    val badLoginRequest = JsonRPCRequest(id = 1, method = LOGIN_METHOD, params = null)
    authSocket.onData(predecessor, Json.encodeToBuffer(badLoginRequest))
  }
}

data class JWTLoginPayload(val jwt: String)
