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
package io.bluebank.braid.core.http

import io.bluebank.braid.core.utils.BraidParameterLookup
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.net.JksOptions
import io.vertx.core.net.KeyCertOptions
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.core.net.PfxOptions
import java.io.ByteArrayOutputStream
import java.io.File

class HttpServerConfig {
  companion object {
    const val CERT_PATH = "server.tls.cert.path"
    const val CERT_SECRET = "server.tls.cert.secret"
    const val CERT_TYPE = "server.tls.cert.type"

    @JvmStatic
    fun defaultServerOptions(): HttpServerOptions {
      val jksPath =
        HttpServerConfig::class.java.`package`.name.replace(".", "/") + "/default.jks"
      val jksBuffer = getResourceAsBuffer(jksPath)
      return HttpServerOptions()
        .setSsl(true)
        .setKeyStoreOptions(
          JksOptions()
            .setValue(jksBuffer)
            .setPassword("8a5500n")
        )
    }

    @JvmStatic
    fun buildFromPropertiesAndVars(): HttpServerOptions {
      val certOptions = getCertOptions()
      return HttpServerOptions().apply {
        isSsl = true
        when (certOptions) {
          null -> isSsl = false
          is JksOptions -> {
            keyStoreOptions = certOptions
          }
          is PfxOptions -> {
            pfxKeyCertOptions = certOptions
          }
          is PemKeyCertOptions -> {
            pemKeyCertOptions = certOptions
          }
          else -> {
            isSsl = false
            log.warn("Unknown key and certificate options: ${certOptions.javaClass.name}. TLS is turned off")
          }
        }
      }
    }

    private fun getResourceAsBuffer(path: String): Buffer? {
      val jksStream = HttpServerConfig::class.java.classLoader.getResourceAsStream(path)
      val baos = ByteArrayOutputStream(jksStream.available())
      while (jksStream.available() > 0) {
        baos.write(jksStream.read())
      }
      val jksBytes = baos.toByteArray()!!
      return Buffer.buffer(jksBytes)
    }

    fun getCertOptions(): KeyCertOptions? {
      val certFile = BraidParameterLookup.getParameter(CERT_PATH)?.checkPathExists() ?: return null
      val secret = BraidParameterLookup.getParameter(CERT_SECRET) ?: return null
      val certType = BraidParameterLookup.getParameter(CERT_TYPE) ?: autoDetectType(certFile) ?: return null

      return when (certType) {
        "p12" -> PfxOptions().apply {
          this.path = certFile
          this.password = secret
        }
        "pem" -> {
          if (secret.checkPathExists() == null) {
            null
          } else {
            PemKeyCertOptions().apply {
              this.certPath = certFile
              this.keyPath = secret
            }
          }
        }
        "jks" -> {
          JksOptions().apply {
            this.path = certFile
            this.password = secret
          }
        }
        else -> {
          error("unknown cert type: $certType")
        }
      }
    }

    private fun autoDetectType(certFile: String): String? {
      val suffix = certFile.substringAfterLast(".")
      return when (suffix) {
        "pfx", "p12" -> "p12"
        "pem" -> "pem"
        "jks" -> "jks"
        else -> null
      }
    }

    private fun String.checkPathExists(): String? {
      return when {
        File(this).exists() -> this
        else -> null
      }
    }

  }
}
