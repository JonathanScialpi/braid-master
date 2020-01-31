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
package io.bluebank.braid.core.json

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import io.bluebank.braid.core.jsonrpc.JsonRPCResultResponse
import io.vertx.core.json.Json
import java.math.BigDecimal

object BraidJacksonInit {
  init {
    val modules = listOf(
      JavaTimeModule(),
      SimpleModule()
        .addSerializer(JsonRPCRequest::class.java, JsonRPCReqestSerializer())
        .addSerializer(
          JsonRPCResultResponse::class.java,
          JsonRPCResultResponseSerializer()
        )
        .addSerializer(
          BigDecimal::class.java,
          com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance
        )
        .addDeserializer(
          BigDecimal::class.java,
          NumberDeserializers.BigDecimalDeserializer()
        )
    )

    listOf(Json.mapper, Json.prettyMapper).forEach { mapper ->
      mapper.registerKotlinModule()
      modules.forEach { module ->
        mapper.registerModule(module)
      }
    }

    Json.mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    Json.prettyMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
  }

  fun init() {
    // automatically init during class load
  }
}
