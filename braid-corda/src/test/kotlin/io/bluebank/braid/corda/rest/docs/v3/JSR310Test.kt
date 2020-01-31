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
package io.bluebank.braid.corda.rest.docs.v3

import io.bluebank.braid.corda.BraidCordaJacksonSwaggerInit
import io.vertx.core.json.Json
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.BeforeClass
import org.junit.Test
import java.time.Duration
import java.time.temporal.ChronoUnit

class JSR310Test{
  companion object {
    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      BraidCordaJacksonSwaggerInit.init()
    }
  }

  @Test
  fun `that WaitTimeUpdate description is correct`() {
    val modelContext = ModelContextV3()
    modelContext.addType(JSR310::class.java)

    val waitTime = modelContext.models.get(JSR310::class.java.name)
    assertThat(waitTime,notNullValue())
    assertThat(waitTime?.properties?.get("duration")?.type, equalTo("string"))
  }

  @Test
  fun `that WaitTimeUpdate serializable is sensible`() {
    ModelContextV3()
    val expected = JSR310(Duration.of(10, ChronoUnit.DAYS))
    val encoded = Json.encode(expected)

    assertEquals("{\"duration\":\"PT240H\"}", encoded)
  }

}