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

import io.vertx.core.json.Json
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import net.corda.core.utilities.NetworkHostAndPort
import org.junit.Test
import kotlin.test.assertEquals

class BraidServerConfigTest {
  @Test
  fun `that we can read config`() {
    val hostAndPort = "localhost:20022"
    val json = json {
      obj(
        "networkHostAndPort" to hostAndPort
      )
    }.toString()
    val actual = Json.decodeValue(json, BraidServerConfig::class.java)
    val expected = BraidServerConfig(
      networkHostAndPort = NetworkHostAndPort.parse(hostAndPort),
      user = BraidServerConfig.DEFAULT_USER,
      password = BraidServerConfig.DEFAULT_PASSWORD,
      openApiVersion = BraidServerConfig.DEFAULT_OPENAPI_VERSION,
      port = BraidServerConfig.DEFAULT_PORT,
      cordapps = listOf(BraidServerConfig.DEFAULT_CORDAPPS_DIR)
    )
    assertEquals(expected, actual)
  }
}