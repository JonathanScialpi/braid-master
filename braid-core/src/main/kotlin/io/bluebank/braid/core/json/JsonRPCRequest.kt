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

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import io.bluebank.braid.core.jsonrpc.JsonRPCRequest

//http://www.jsonrpc.org/specification#parameter_structures
// so only need to support a list and a map i think...
// certainly we're only calling this using a list for parameters at the mo...
// will come back to map once we've figured out the structure - presumably Map<String, Any>
class JsonRPCReqestSerializer :
  StdSerializer<JsonRPCRequest>(JsonRPCRequest::class.java) {

  override fun serialize(
    value: JsonRPCRequest,
    generator: JsonGenerator,
    provider: SerializerProvider
  ) {
    generator.writeStartObject()
    generator.writeStringField("jsonrpc", value.jsonrpc)
    generator.writeNumberField("id", value.id)
    generator.writeStringField("method", value.method)

    when (value.params) {
      is List<Any?> -> {
        generator.writeArrayFieldStart("params")
        value.params.forEach { generator.writeObject(it) }
        generator.writeEndArray()
      }
      else -> UnsupportedOperationException("need to handle JsonRPCRequest map parameters")
    }

    generator.writeBooleanField("streamed", value.streamed)

    generator.writeEndObject()
  }
}