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
package io.bluebank.braid.corda.serialisation.serializers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class X509Serializer : StdSerializer<X509Certificate>(X509Certificate::class.java) {
  override fun serialize(
      value: X509Certificate,
      generator: JsonGenerator,
      provider: SerializerProvider
  ) {
    generator.writeBinary(value.encoded)
  }
}

class X509Deserializer :
    StdDeserializer<X509Certificate>(X509Certificate::class.java) {

  override fun deserialize(
      parser: JsonParser,
      context: DeserializationContext
  ): X509Certificate {

    val cf = CertificateFactory.getInstance("X.509")
    return cf.generateCertificate(ByteArrayInputStream(parser.binaryValue)) as X509Certificate
  }
}
