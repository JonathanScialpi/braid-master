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
package io.bluebank.braid.corda.rest

import io.bluebank.braid.core.socket.findFreePort
import io.netty.handler.codec.http.HttpHeaderValues
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import net.corda.core.utilities.contextLogger
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Suppress("DEPRECATION")
@RunWith(VertxUnitRunner::class)
abstract class AbstractRestMounterTest(openApiVersion: Int = 2) {

  companion object {
    val log = contextLogger()
  }

  private val port = findFreePort()
  private val service = TestServiceApp(port = port, service = TestService(), openApiVersion = openApiVersion)
  private val client = service.server.vertx.createHttpClient(HttpClientOptions().apply {
    defaultHost = "localhost"
    defaultPort = port
    isSsl = true
    isTrustAll = true
    isVerifyHost = false
  })

  @Before
  fun before(context: TestContext) {
    service.whenReady().setHandler(context.asyncAssertSuccess())
  }

  @After
  fun after() {
    client.close()
    service.shutdown()
  }

  @Test
  fun `test that we can mount rest endpoints and access via swagger`(context: TestContext) {
    val async1 = context.async()
    log.info("calling GET ${TestServiceApp.SWAGGER_ROOT}/swagger.json")
    client.get("${TestServiceApp.SWAGGER_ROOT}/swagger.json") { httpResponse ->
      httpResponse.bodyHandler { buffer ->
        try {
          val s = buffer.toString()
          JsonObject(s)
        } catch (err: Throwable) {
          context.fail(err)
        }
        async1.complete()
      }
    }
      .exceptionHandler(context::fail)
      .end()
    async1.awaitSuccess()

    val async2 = context.async()
    log.info("calling GET ${TestServiceApp.SWAGGER_ROOT}/")
    client.get("${TestServiceApp.SWAGGER_ROOT}/") { httpResponse ->
      httpResponse.bodyHandler { buffer ->
        val body = buffer.toString()
        context.assertTrue(body.contains("<title>Swagger UI</title>", true))
        async2.complete()
      }
    }
      .exceptionHandler(context::fail)
      .end()
    async2.awaitSuccess()

    val async3 = context.async()
    log.info("calling GET ${TestServiceApp.REST_API_ROOT}/hello")
    client.get("${TestServiceApp.REST_API_ROOT}/hello") {
      it.bodyHandler {
        context.assertEquals("hello", it.toString())
        async3.complete()
      }
    }
      .exceptionHandler(context::fail)
      .end()
    async3.awaitSuccess()

    val async4 = context.async()
    log.info("calling GET ${TestServiceApp.REST_API_ROOT}/buffer")
    client.get("${TestServiceApp.REST_API_ROOT}/buffer") { response ->
      response.bodyHandler { body ->
        context.assertEquals(
          HttpHeaderValues.APPLICATION_OCTET_STREAM.toString(),
          response.getHeader(HttpHeaders.CONTENT_TYPE)
        )
        context.assertEquals("hello", body.toString())
        async4.complete()
      }
    }
      .exceptionHandler(context::fail)
      .end()
    async4.awaitSuccess()

    val async5 = context.async()
    log.info("calling GET ${TestServiceApp.REST_API_ROOT}/bytearray")
    client.get("${TestServiceApp.REST_API_ROOT}/bytearray")
      .exceptionHandler(context::fail)
      .handler { response ->
        response.bodyHandler { body ->
          context.assertEquals(
            HttpHeaderValues.APPLICATION_OCTET_STREAM.toString(),
            response.getHeader(HttpHeaders.CONTENT_TYPE)
          )
          context.assertEquals("hello", body.toString())
          async5.complete()
        }
      }
      .end()
    async5.awaitSuccess()

    val async6 = context.async()
    log.info("calling GET ${TestServiceApp.REST_API_ROOT}/bytebuf")
    client.get("${TestServiceApp.REST_API_ROOT}/bytebuf")
      .exceptionHandler(context::fail)
      .handler { response ->
        response.bodyHandler { body ->
          context.assertEquals(
            HttpHeaderValues.APPLICATION_OCTET_STREAM.toString(),
            response.getHeader(HttpHeaders.CONTENT_TYPE)
          )
          context.assertEquals("hello", body.toString())
          async6.complete()
        }
      }
      .end()
    async6.awaitSuccess()

    val async7 = context.async()
    log.info("calling GET ${TestServiceApp.REST_API_ROOT}/bytebuffer")
    client.get("${TestServiceApp.REST_API_ROOT}/bytebuffer")
      .exceptionHandler(context::fail)
      .handler { response ->
        response.bodyHandler { body ->
          context.assertEquals(
            HttpHeaderValues.APPLICATION_OCTET_STREAM.toString(),
            response.getHeader(HttpHeaders.CONTENT_TYPE)
          )
          context.assertEquals("hello", body.toString())
          async7.complete()
        }
      }
      .end()
    async7.awaitSuccess()

    val async8 = context.async()
    val bytes = Buffer.buffer("hello")
    log.info("calling POST ${TestServiceApp.REST_API_ROOT}/doublebuffer")
    client.post("${TestServiceApp.REST_API_ROOT}/doublebuffer")
      .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM)
      .putHeader(HttpHeaders.CONTENT_LENGTH, bytes.length().toString())
      .exceptionHandler(context::fail)
      .handler { response ->
        context.assertEquals(2, response.statusCode() / 100)
        response.bodyHandler { body ->
          context.assertEquals(
            HttpHeaderValues.APPLICATION_OCTET_STREAM.toString(),
            response.getHeader(HttpHeaders.CONTENT_TYPE)
          )
          context.assertEquals("hellohello", body.toString())
          async8.complete()
        }
      }
      .end(bytes)
    async8.awaitSuccess()

    val async9 = context.async()
    var token = ""
    log.info("calling POST ${TestServiceApp.REST_API_ROOT}/login")
    client.post("${TestServiceApp.REST_API_ROOT}/login")
      .exceptionHandler(context::fail)
      .handler { response ->
        response.bodyHandler { body ->
          token = body.toString()
          async9.complete()
        }
      }
      .setChunked(true)
      .end(Json.encode(LoginRequest(user = "sa", password = "admin")))

    async9.awaitSuccess()

    val async10 = context.async()
    log.info("calling POST ${TestServiceApp.REST_API_ROOT}/echo")
    client.post("${TestServiceApp.REST_API_ROOT}/echo")
      .exceptionHandler(context::fail)
      .handler { response ->
        response.bodyHandler { body ->
          context.assertEquals("echo: hello", body.toString())
          async10.complete()
        }
      }
      .putHeader("Authorization", "Bearer $token")
      .setChunked(true)
      .end("hello")

    async10.awaitSuccess()

    val async11 = context.async()
    log.info("calling GET ${TestServiceApp.REST_API_ROOT}/headers/list/string with the required header 'x-list-string'. Should return an array containing the value of the header")
    val headerValues = listOf(1, 2, 3)
    client.get("${TestServiceApp.REST_API_ROOT}/headers/list/string")
      .putHeader(X_HEADER_LIST_STRING, headerValues.map { it.toString() })
      .exceptionHandler(context::fail)
      .handler { response ->
        response.bodyHandler { body ->
          val bodyArray = body.toJsonArray()
          context.assertEquals(headerValues.map { it.toString() }, bodyArray.list)
          async11.complete()
        }
      }
      .end()
    async11.awaitSuccess()

    val async12 = context.async()
    log.info("calling GET ${TestServiceApp.REST_API_ROOT}/headers/list/int with the required header 'x-list-string'. Should return an array containing the value of the header")
    client.get("${TestServiceApp.REST_API_ROOT}/headers/list/int")
      .putHeader(X_HEADER_LIST_STRING, headerValues.map { it.toString() })
      .exceptionHandler(context::fail)
      .handler { response ->
        response.bodyHandler { body ->
          val bodyArray = body.toJsonArray()
          context.assertEquals(headerValues, bodyArray.list)
          async12.complete()
        }
      }
      .end()
    async12.awaitSuccess()

    val async13 = context.async()
    log.info("calling GET ${TestServiceApp.REST_API_ROOT}/headers with the header 'x-string'. Should return an array containing the value of the x-string header")
    client.get("${TestServiceApp.REST_API_ROOT}/headers")
      .putHeader(X_HEADER_LIST_STRING, headerValues.map { it.toString() })
      .exceptionHandler(context::fail)
      .handler { response ->
        response.bodyHandler { body ->
          val bodyArray = body.toJsonArray()
          context.assertEquals(headerValues, bodyArray.list)
          async13.complete()
        }
      }
      .end()
    async13.awaitSuccess()

    val async14 = context.async()
    log.info("calling GET ${TestServiceApp.REST_API_ROOT}/headers/optional with the header 'x-string'. Should return the value of the x-string header")
    val testString = "this is a test"
    client.get("${TestServiceApp.REST_API_ROOT}/headers/optional")
      .putHeader(X_HEADER_STRING, testString)
      .exceptionHandler(context::fail)
      .handler { response ->
        response.bodyHandler { body ->
          context.assertEquals(testString, body.toString())
          async14.complete()
        }
      }
      .end()
    async14.awaitSuccess()

    val async15 = context.async()
    log.info("calling GET ${TestServiceApp.REST_API_ROOT}/headers/optional without the optioinal header 'x-string'. Should return 'null'")
    client.get("${TestServiceApp.REST_API_ROOT}/headers/optional")
      // N.B. no header set
      .exceptionHandler(context::fail)
      .handler { response ->
        response.bodyHandler { body ->
          context.assertEquals("null", body.toString())
          async15.complete()
        }
      }
      .end()
    async15.awaitSuccess()

    val async16 = context.async()
    log.info("calling GET ${TestServiceApp.REST_API_ROOT}/headers/non-optional without the required header 'x-string'. This will expect an assertion to fail in the server. This is okay.")
    client.get("${TestServiceApp.REST_API_ROOT}/headers/non-optional")
      // N.B. no header set
      .exceptionHandler(context::fail)
      .handler { response ->
        context.assertEquals(Response.Status.BAD_REQUEST.statusCode, response.statusCode())
        async16.complete()
      }
      .end()
    async16.awaitSuccess()
  }

  @Test
  fun `test that method that fails returns all headers`(context: TestContext) {
    val async1 = context.async()
    client.get("${TestServiceApp.REST_API_ROOT}/willfail")
      .exceptionHandler(context::fail)
      .handler {
        context.assertEquals(HTTP_UNPROCESSABLE_STATUS_CODE, it.statusCode())
        context.assertEquals("on purpose failure", it.statusMessage())
        context.assertEquals(MediaType.APPLICATION_JSON, it.getHeader(HttpHeaders.CONTENT_TYPE))

        it.bodyHandler { buffer ->
          val jo = JsonObject(buffer)
          context.assertEquals(RuntimeException::class.java.simpleName, jo.getString("type"))
          context.assertEquals("on purpose failure", jo.getString("message"))
          async1.complete()
        }
      }
      .end()
  }

  @Test
  fun `that map of numbers to numbers can be mapped to map of string to string`(context: TestContext) {
    val async1 = context.async()
    val request = mapOf(1 to 1, 2 to 2, 3 to 3)
    val expected = request.map {
      it.key.toString() to it.value.toString()
    }.toMap()
    client.post("${TestServiceApp.REST_API_ROOT}/map-numbers-to-string")
      .exceptionHandler(context::fail)
      .handler { response ->
        context.assertEquals(200, response.statusCode())
        response.bodyHandler { buffer ->
          val result = Json.decodeValue(buffer.toString(), Map::class.java)
          context.assertEquals(expected, result)
          async1.complete()
        }
      }
      .end(Json.encode(request))
  }

  @Test
  fun `that map of strings to list of numbers can be mapped to map of string to list of strings`(context: TestContext) {
    val async1 = context.async()
    val result = mapOf(1 to listOf(1, 2, 3), 2 to listOf(4, 5, 6), 3 to listOf(7, 8, 9))
    val expected = result.map { entry ->
      entry.key.toString() to entry.value.map { value -> value.toString() }
    }.toMap()
    client.post("${TestServiceApp.REST_API_ROOT}/map-list-of-numbers-to-map-of-list-of-string")
      .exceptionHandler(context::fail)
      .handler { response ->
        context.assertEquals(200, response.statusCode())
        response.bodyHandler { buffer ->
          val decoded = Json.decodeValue(buffer.toString(), Map::class.java)
          context.assertEquals(expected, decoded)
          async1.complete()
        }
      }
      .end(Json.encode(result))
  }

}