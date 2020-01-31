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
@file:Suppress("DEPRECATION")

package io.bluebank.braid.corda.rest

import io.bluebank.braid.corda.BraidConfig
import io.bluebank.braid.corda.BraidServer
import io.bluebank.braid.core.http.HttpServerConfig
import io.bluebank.braid.core.security.JWTUtils
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTOptions
import java.io.File
import java.io.FileOutputStream

class TestServiceApp(
  port: Int,
  private val service: TestService,
  openApiVersion: Int = 3,
  private val httpServerOptions: HttpServerOptions = HttpServerConfig.defaultServerOptions()
) {

  companion object {
    const val SWAGGER_ROOT = ""
    const val REST_API_ROOT = "/rest"
    @JvmStatic
    fun main(args: Array<String>) {
      TestServiceApp(8080, TestService(), 3)
    }
  }

  private val tempJKS = File.createTempFile("temp-", ".jceks")
  private val jwtSecret = "secret"
  private lateinit var jwtAuth: JWTAuth

  val server: BraidServer

  init {
    val thisObj = this
    server = BraidConfig()
      .withPort(port)
      .withService(service)
      .withAuthConstructor(this::createAuthProvider)
      .withHttpServerOptions(httpServerOptions)
      .withRestConfig(
        RestConfig()
          .withServiceName("my-service")
          .withAuthSchema(AuthSchema.Token)
          .withSwaggerPath(SWAGGER_ROOT)
          .withApiPath(REST_API_ROOT)
//          enable the next line to generate the swagger definition everytime
//          .withDebugMode() //
          .withOpenApiVersion(openApiVersion)
          .withPaths {
            group("Test Service") {
              unprotected {
                get("/hello-async", service::sayHelloAsync)
                get("/quiet-async-void", service::quietAsyncVoid)
                get("/quiet-async-unit", service::quietAsyncUnit)
                get("/quiet-unit", service::quietUnit)
                post("/login", thisObj::login)
                get("/hello", service::sayHello)
                get("/buffer", service::getBuffer)
                get("/bytearray", service::getByteArray)
                get("/bytebuf", service::getByteBuf)
                get("/bytebuffer", service::getByteBuffer)
                post("/doublebuffer", service::doubleBuffer)
                post("/custom", service::somethingCustom)
                get("/stringlist", service::returnsListOfStuff)
                get("/willfail", service::willFail)
                get("/headers/list/string", service::headerListOfStrings)
                get("/headers/list/int", service::headerListOfInt)
                get("/headers", service::headers)
                get("/headers/optional", service::optionalHeader)
                get("/headers/non-optional", service::nonOptionalHeader)
                post("/map-numbers-to-string", service::mapNumbersToStrings)
                post(
                  "/map-list-of-numbers-to-map-of-list-of-string",
                  service::mapMapOfNumbersToMapOfStrings
                )
                get("/throws-error", service::throwCordaException)
                post("/sum", service::sum)
              }
              protected {
                post("/echo", service::echo)
                get("/whoami", service::whoami)
              }
            }
          })
      .bootstrapBraid(TestAppServiceHub())
  }

  fun whenReady(): Future<String> = server.whenReady()
  fun shutdown() = server.shutdown()

  @Suppress("MemberVisibilityCanBePrivate")
  fun login(request: LoginRequest): String {
    if (request == LoginRequest("sa", "admin")) {
      @Suppress("DEPRECATION")
      return jwtAuth.generateToken(
        JsonObject().put("user", request.user),
        JWTOptions().setExpiresInMinutes(24 * 60)
      )
    } else {
      throw RuntimeException("failed to authenticate")
    }
  }

  private fun createAuthProvider(vertx: Vertx): AuthProvider {
    ensureJWTKeyStoreExists()
    @Suppress("DEPRECATION")
    return JWTAuth.create(
      vertx, JsonObject().put(
        "keyStore", JsonObject()
          .put("path", tempJKS.absolutePath)
          .put("type", "jceks")
          .put("password", jwtSecret)
      )
    ).apply {
      jwtAuth = this
    }
  }

  private fun ensureJWTKeyStoreExists() {
    val ks = JWTUtils.createSimpleJWTKeyStore(jwtSecret)
    FileOutputStream(tempJKS.absoluteFile).use {
      ks.store(it, jwtSecret.toCharArray())
      it.flush()
    }
  }
}