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
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class JsonRPCResultResponseTest {
  @Before
  fun before() {
    BraidJacksonInit.init()
  }

  @Test
  fun shouldBeAbleToPreserveTypeInformationDuringSerialisation() {
    val expected =
      "{\"result\":{\"type\":\"MeteringModelData\",\"someString\":\"wobble\"},\"id\":1,\"jsonrpc\":\"2.0\"}"
    val jsonResponse = JsonRPCResultResponse(id = 1, result = MeteringModelData("wobble"))
    Assert.assertEquals(expected, Json.encode(jsonResponse))
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