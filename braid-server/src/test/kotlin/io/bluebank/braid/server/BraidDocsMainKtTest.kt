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

import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.json.JsonObject
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.stream.Collectors.toSet

private val log = loggerFor<BraidDocsMainKtTest>()

class BraidDocsMainKtTest {
  private lateinit var json: JsonObject
  private lateinit var swagger: String

  @Before
  fun setUp() {
    val jars = listOf(
//      "/Users/fuzz/dev/web3j/corda/console/src/test/resources/cordapps/obligation-0.1.jar",
      "https://repo1.maven.org/maven2/net/corda/corda-finance-contracts/4.0/corda-finance-contracts-4.0.jar",
      "https://repo1.maven.org/maven2/net/corda/corda-finance-workflows/4.0/corda-finance-workflows-4.0.jar"
    )
    try {
      swagger = generateSwaggerText(3, jars)
      json = JsonObject(swagger)
    } catch (err: Throwable) {
      log.info("BraidDocsMainKtTest init error", err)
      throw err;
    }
  }

  @Test
  fun `that we can generate a swagger definition V2`() {
    assertNotNull(json.getJsonObject("paths")?.getJsonObject("/cordapps/corda-finance-workflows/flows/net.corda.finance.flows.CashExitFlow"))
  }

  @Test
  fun `that we can generate a swagger definition for V3`() {
    assertNotNull(json.getJsonObject("paths")?.getJsonObject("/cordapps/corda-finance-workflows/flows/net.corda.finance.flows.CashExitFlow"))
  }

  @Test
  fun `should not have ProgressTracker`() {
    val schemaNames = json.getJsonObject("components").getJsonObject("schemas").map.keys
      .stream()
      .filter { it.contains("ProgressTracker") }
      .collect(toSet())

    assertThat(schemaNames.toString(), schemaNames.size, equalTo(0));
  }

  @Test
  fun `should not have rx Observable`() {
    val schemaNames = json.getJsonObject("components").getJsonObject("schemas").map.keys
      .stream()
      .filter { it.contains("Observable") }
      .collect(toSet())

    assertThat(schemaNames.toString(), schemaNames.size, equalTo(0));
  }

}