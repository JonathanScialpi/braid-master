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
package io.bluebank.braid.core.jsonrpc

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.vertx.core.json.Json
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class ParameterTests {
  data class Receiver(val params: Any)
  data class SenderArray(val params: List<Int>)
  data class SenderObject(val params: Params)
  data class Params(val name: String, val age: Int)

  @Before
  fun before() {
    with(KotlinModule()) {
      Json.mapper.registerModule(this)
      Json.prettyMapper.registerModule(this)
    }
  }

  @Test
  fun deserialiseList() {
    val encoded = Json.encode(SenderArray(listOf(1, 2, 3)))
    val decoded = Json.decodeValue(encoded, Receiver::class.java)
    assertTrue(decoded.params is List<*>)
  }

  @Test
  fun deserialiseMap() {
    val encoded = Json.encode(SenderObject(Params("Fred", 40)))
    val decoded = Json.decodeValue(encoded, Receiver::class.java)
    assertTrue(decoded.params is Map<*, *>)
  }
}
