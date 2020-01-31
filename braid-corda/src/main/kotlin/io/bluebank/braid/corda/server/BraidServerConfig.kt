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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.bluebank.braid.core.json.BraidJacksonInit
import io.vertx.core.json.Json
import net.corda.core.utilities.NetworkHostAndPort
import java.io.File
import kotlin.system.exitProcess

@Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection")
data class BraidServerConfig(
  val networkHostAndPort: NetworkHostAndPort = DEFAULT_NODE_ADDRESS,
  val user: String = DEFAULT_USER,
  val password: String = DEFAULT_PASSWORD,
  val openApiVersion: Int = DEFAULT_OPENAPI_VERSION,
  val port: Int = DEFAULT_PORT,
  val cordapps: List<String> = listOf(DEFAULT_CORDAPPS_DIR)
) {

  companion object {
    val DEFAULT_NODE_ADDRESS = NetworkHostAndPort.parse("localhost:10006")
    const val DEFAULT_USER = ""
    const val DEFAULT_PASSWORD = ""
    @Suppress("SpellCheckingInspection")
    const val DEFAULT_OPENAPI_VERSION = 3
    const val DEFAULT_PORT = 9000
    @Suppress("SpellCheckingInspection")
    const val DEFAULT_CORDAPPS_DIR = "./cordapps"
    private val configFile = File("braid.conf")

    init {
      BraidJacksonInit.init()
    }

    @JsonCreator
    @JvmStatic
    fun creator(
      @JsonProperty("networkHostAndPort") networkHostAndPort: String,
      @JsonProperty("user") user: String? = null,
      @JsonProperty("password") password: String? = null,
      @JsonProperty("openApiVersion") openApiVersion: Int? = null,
      @JsonProperty("port") port: Int? = null,
      @JsonProperty("cordapps") cordapps: List<String>? = null
    ): BraidServerConfig {
      return BraidServerConfig(
        NetworkHostAndPort.parse(networkHostAndPort),
        user ?: DEFAULT_USER,
        password ?: DEFAULT_PASSWORD,
        openApiVersion ?: DEFAULT_OPENAPI_VERSION,
        port ?: DEFAULT_PORT,
        cordapps ?: listOf(DEFAULT_CORDAPPS_DIR)
      )
    }

    private val argsHandlers =
      listOf<(BraidServerConfig, Array<String>) -> BraidServerConfig>(
        { config: BraidServerConfig, args ->
          config.copy(
            networkHostAndPort = NetworkHostAndPort.parse(
              args[0]
            )
          )
        },
        { config: BraidServerConfig, args -> config.copy(user = args[1]) },
        { config: BraidServerConfig, args -> config.copy(password = args[2]) },
        { config: BraidServerConfig, args -> config.copy(port = args[3].toInt()) },
        { config: BraidServerConfig, args -> config.copy(openApiVersion = args[4].toInt()) },
        { config: BraidServerConfig, args -> config.copy(cordapps = args.drop(5)) }
      )

    fun config(args: Array<String>): BraidServerConfig {
      val parsedConfig = parseConfig(args)
      return processArgs(args, parsedConfig)
        .also {
          println("Running with config: $it")
        }
    }

    private fun processArgs(
      args: Array<String>,
      parsedConfig: BraidServerConfig
    ): BraidServerConfig {
      return if (args.isEmpty()) {
        println("no arguments provided. using the default config ${parsedConfig}")
        parsedConfig
      } else {
        argsHandlers.take(args.size).fold(parsedConfig) { c, op -> op(c, args) }
      }
    }

    private fun parseConfig(args: Array<String>): BraidServerConfig {
      val parsedConfig = when {
        !configFile.exists() && args.isEmpty() -> {
          println("Did not find braid.conf and no command line arguments provided")
          println("Usage: Please supply braid.conf or BraidMainKt <node address> <username> <password> <port> <openApiVersion> [<cordaAppJar1> <cordAppJar2> ....]")
          exitProcess(0)
        }
        configFile.exists() ->
          Json.decodeValue(
            configFile.readText(Charsets.UTF_8),
            BraidServerConfig::class.java
          )
        else -> BraidServerConfig()
      }
      return parsedConfig
    }
  }
}
