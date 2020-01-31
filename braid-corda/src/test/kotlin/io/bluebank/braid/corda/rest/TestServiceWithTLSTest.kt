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

import io.bluebank.braid.core.async.catch
import io.bluebank.braid.core.async.finally
import io.bluebank.braid.core.async.onSuccess
import io.bluebank.braid.core.http.HttpServerConfig
import io.bluebank.braid.core.http.body
import io.bluebank.braid.core.http.getFuture
import io.bluebank.braid.core.socket.findFreePort
import io.bluebank.braid.core.utils.BraidParameterLookup
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.core.net.PemTrustOptions
import io.vertx.core.net.TrustOptions
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.AfterTest

@RunWith(VertxUnitRunner::class)
class TestServiceWithTLSTest {
  private val port = findFreePort()
  private val service = TestService()
  private val vertx = Vertx.vertx()

  @AfterTest
  fun after(context: TestContext) {
    val async = context.async()
    vertx.close {
      async.complete()
    }
  }

  @Test
  fun `test with jks`(context: TestContext) {
    val cert = "certs/certificate.jks"
    val certSecret = "password"
    val certPath = getResourceFile(cert).canonicalPath
    setupClientAndCallService(certPath, certSecret, context)
  }

  @Test
  fun `test with pkcs12`(context: TestContext) {
    val cert = "certs/certificate.p12"
    val certSecret = "password"
    val certPath = getResourceFile(cert).canonicalPath
    setupClientAndCallService(certPath, certSecret, context)
  }

  @Test
  fun `test with pem`(context: TestContext) {
    val certPath = getResourceFile("certs/certificate.pem").canonicalPath
    val secretPath = getResourceFile("certs/key.pem").canonicalPath
    setupClientAndCallService(certPath, secretPath, context)
  }

  private fun setupClientAndCallService(certPath: String, certSecret: String, context: TestContext) {
    setCertProperties(certPath, certSecret)
    val client = setupClient()
    callService(context, client)
  }

  private fun setupClient(): HttpClient {
    @Suppress("USELESS_CAST")  // because Kotlin compiler complains
    val clientTrustOptions: TrustOptions = HttpServerConfig.getCertOptions()?.let {
      when (it) {
        is TrustOptions -> it as TrustOptions
        is PemKeyCertOptions -> PemTrustOptions().addCertPath(it.certPath)
        else -> error("options was not ${TrustOptions::class.simpleName} instead got ${it.javaClass.simpleName}")
      }
    } ?: error("got null for ${TrustOptions::class.simpleName}")

    val client = vertx.createHttpClient(HttpClientOptions().apply {
      trustOptions = clientTrustOptions
      isSsl = true
      defaultHost = "localhost"
      defaultPort = port
    })
    return client
  }

  private fun callService(context: TestContext, client: HttpClient) {
    val app = TestServiceApp(port, service, httpServerOptions = HttpServerConfig.buildFromPropertiesAndVars())
    val async = context.async()
    app.whenReady()
      .compose { client.getFuture("/swagger.json") }
      .compose { it.body<String>() }
      .finally {
        client.close()
        app.shutdown()
      }
      .onSuccess { async.complete() }
      .catch { context.fail(it.cause) }
  }

  private fun setCertProperties(certPath: String, certSecret: String) {
    System.setProperty("${BraidParameterLookup.PREFIX}.${HttpServerConfig.CERT_PATH}", certPath)
    System.setProperty("${BraidParameterLookup.PREFIX}.${HttpServerConfig.CERT_SECRET}", certSecret)
  }

  private fun getResourceFile(resourcePath: String): File {
    val relPath = this.javaClass.protectionDomain.codeSource.location.file
    val resourcesPath = File(relPath, "../../src/test/resources").canonicalFile
    val jksFile = File(resourcesPath, resourcePath)
    return jksFile
  }
}