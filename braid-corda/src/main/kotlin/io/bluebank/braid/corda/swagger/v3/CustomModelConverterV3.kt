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
package io.bluebank.braid.corda.swagger.v3

import com.fasterxml.jackson.databind.JavaType
import io.bluebank.braid.corda.rest.docs.BraidSwaggerError
import io.netty.buffer.ByteBuf
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.media.*
import io.vertx.core.buffer.Buffer
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.loggerFor
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.util.*

/**
 * From https://github.com/swagger-api/swagger-core/issues/1167
 *
 * to be used when calling BraidCordaJacksonInit.init()
 *
 */
class CustomModelConverterV3 : ModelConverter {

  companion object {
    private val log = loggerFor<CustomModelConverterV3>()
  }

  override fun resolve(
    type: AnnotatedType,
    context: io.swagger.v3.core.converter.ModelConverterContext,
    chain: MutableIterator<ModelConverter>?
  ): Schema<*>? {
    val jsonType = Json.mapper().constructType(type.type)
    return try {
      when (jsonType) {
        null -> chain?.next()?.resolve(type, context, chain)
        else -> {
          val clazz = jsonType.rawClass
          when {
            clazz.isBinary() -> BinarySchema()
            PublicKey::class.java.isAssignableFrom(clazz) -> publicKeySchema()
            Class::class.java.isAssignableFrom(clazz) -> classSchema()
            SecureHash::class.java.isAssignableFrom(clazz) || SecureHash.SHA256::class.java.isAssignableFrom(clazz) -> secureHashSchema()
            CordaX500Name::class.java.isAssignableFrom(clazz) -> cordaX500NameSchema()
            X509Certificate::class.java.isAssignableFrom(clazz) -> x509Schema()
            CertPath::class.java.isAssignableFrom(clazz) -> certPathSchema()
            Currency::class.java.isAssignableFrom(clazz) -> currencySchema()
            Amount::class.java.isAssignableFrom(clazz) -> geAmountSchema(jsonType)
            Issued::class.java.isAssignableFrom(clazz) -> getIssuedSchema(context, jsonType)
            Throwable::class.java.isAssignableFrom(clazz) -> {
              type.type = BraidSwaggerError::class.java
              chain?.next()?.resolve(type, context, chain)
            }
            else -> chain?.next()?.resolve(type, context, chain)
          }
        }
      }
    } catch (e: Throwable) {
      log.error("failed to parse or resolve type: ${type.type}")
      throw e
    }
  }

  private fun Type.isBinary(): Boolean {
    return when (this) {
      Buffer::class.java,
      ByteArray::class.java,
      ByteBuffer::class.java,
      ByteBuf::class.java -> true
      else -> false
    }
  }

  private fun geAmountSchema(jsonType: JavaType): Schema<*> {
    // String and Currency get created as their own types
    val boundType = jsonType.bindings.getBoundType(0)
    return when {
      boundType?.rawClass == Currency::class.java -> amountCurrencySchema()
      else -> generalAmountSchema()
    }
  }

  private fun getIssuedSchema(
    context: io.swagger.v3.core.converter.ModelConverterContext,
    jsonType: JavaType
  ): Schema<*> {
    val boundType = jsonType.bindings.getBoundType(0)
    return if (boundType != null && (boundType.rawClass == Currency::class.java || boundType.rawClass == String::class.java)) {
      ObjectSchema()
        .name("IssuedCurrency")
        .addProperties("issuer", context.resolve(AnnotatedType(PartyAndReference::class.java)))
        .addProperties("product", context.resolve(AnnotatedType(boundType)))
        .addRequiredItem("issuer")
        .addRequiredItem("product")
    } else {
      ObjectSchema()
        .name("Issued")
        .addProperties("issuer", context.resolve(AnnotatedType(PartyAndReference::class.java)))
        .addProperties("product", context.resolve(AnnotatedType(boundType)))
        .addProperties("_productType", StringSchema().example("java.util.Currency"))
        .addRequiredItem("issuer")
        .addRequiredItem("product")
        .addRequiredItem("_productType")
    }
  }

  private fun publicKeySchema() = StringSchema()
    .example("GfHq2tTVk9z4eXgyUuofmR16H6j7srXt8BCyidKdrZL5JEwFqHgDSuiinbTE")
    .description("Base 58 Encoded Public Key")

  private fun classSchema() = StringSchema()
    .example("java.lang.Object")
    .description("Java class name")

  private fun secureHashSchema() = StringSchema()
    .example("GfHq2tTVk9z4eXgyUuofmR16H6j7srXt8BCyidKdrZL5JEwFqHgDSuiinbTE")
    .description("Base 58 Encoded Secure Hash")

  private fun cordaX500NameSchema() = StringSchema()
    .example("O=Bank A, L=London, C=GB")
    .description("CordaX500Name encoded Party")

  private fun x509Schema() = ByteArraySchema()
    .description("X509 encoded certificate")

  private fun certPathSchema() = ByteArraySchema()
    .description("X509 encoded certificate PKI path")

  private fun opaqueBytesSchema() = StringSchema()
    .example("736F6D654279746573")
    .description("Hex encoded Byte Array")

  private fun currencySchema() = StringSchema()
    .example("GBP")
    .description("3 digit ISO 4217 code of the currency")

  private fun amountCurrencySchema() = ObjectSchema()
    .name("AmountCurrency")
    .addProperties(
      "quantity", IntegerSchema()
      .example(100)
      .description("total amount in minor units")
    )
    .addProperties("displayTokenSize", NumberSchema().example("0.01"))
    .addProperties("token", StringSchema().example("GBP"))
    .addRequiredItem("quantity")
    .addRequiredItem("displayTokenSize")
    .addRequiredItem("token")

  private fun generalAmountSchema() = ObjectSchema()
    .name("Amount")
    .addProperties(
      "quantity", IntegerSchema()
      .example(100)
      .description("total amount in minor units")
    )
    .addProperties("displayTokenSize", NumberSchema().example("0.01"))
    .addProperties("token", StringSchema().example("GBP"))
    .addRequiredItem("quantity")
    .addRequiredItem("displayTokenSize")
    .addRequiredItem("token")
    .addProperties(
      "_tokenType", StringSchema()
      .example("net.corda.core.contracts.Issued")
    )

}