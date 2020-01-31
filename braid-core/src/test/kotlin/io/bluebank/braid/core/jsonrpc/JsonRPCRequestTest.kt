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

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.bluebank.braid.core.json.BraidJacksonInit
import io.vertx.core.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class JsonRPCRequestTest {
  @Before
  fun before() {
    BraidJacksonInit.init()
  }

  @Test
  fun testDeser() {
    val str = """{"jsonrpc":"2.0","method":"add","params":1,"id":-9007199254740991}"""
    val result = Json.decodeValue(str, JsonRPCRequest::class.java)
    assertEquals(-9007199254740991, result.id)
  }

  @Test
  fun shouldBeAbleToPreserveTypeInformationInMultipleParameterMethodsWithoutMethod() {
    val expected =
      "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"fish\",\"params\":[\"wibble\",{\"type\":\"MeteringModelData\",\"someString\":\"wobble\"}],\"streamed\":false}"
    val params = listOf<Any?>("wibble", MeteringModelData("wobble"))
    val jsonRequest =
      JsonRPCRequest(id = 1, method = "fish", params = params, streamed = false)
    val actual = Json.encode(jsonRequest)
    assertEquals(expected, actual)
  }

  @JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
  )
  @JsonSubTypes(
    JsonSubTypes.Type(value = MeteringModelData::class, name = "MeteringModelData")
  )
  interface ModelData

  data class MeteringModelData(val someString: String) : ModelData

}
