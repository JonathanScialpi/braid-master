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

import io.bluebank.braid.core.utils.toJarsClassLoader
import io.bluebank.braid.core.utils.tryWithClassLoader
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BraidDocsMainTest {
  companion object {
    val jars = listOf(
      "https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda-finance-workflows/4.1/corda-finance-workflows-4.1.jar",
      "https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda-finance-contracts/4.1/corda-finance-contracts-4.1.jar"
    )
    val swagger = tryWithClassLoader(jars.toJarsClassLoader()) {
      BraidDocsMain().swaggerText(3)
    }
    val openApi = Json.mapper().readValue(swagger, OpenAPI::class.java)
  }

  @Test
  fun `that we can generate swagger json`() {
    assertEquals(1, openApi.servers.size)
    assertEquals("http://localhost:8080/api/rest", openApi.servers[0].url)
    assertTrue(openApi.paths.containsKey("/network/nodes"))
    assertTrue(openApi.paths.containsKey("/cordapps"))
    assertTrue(openApi.paths.containsKey("/cordapps/corda-finance-workflows/flows/net.corda.finance.flows.CashPaymentFlow"))

  }

  @Test
  fun `that we can generate InvocationError`() {
    assertTrue(openApi.components.schemas.containsKey("InvocationError"))
  }

  @Test
  fun `that we can generate Contract json`() {
    assertTrue(openApi.components.schemas.containsKey("net.corda.finance.contracts.Commodity"))
  }

}