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

import io.bluebank.braid.corda.BraidCordaJacksonSwaggerInit
import io.bluebank.braid.corda.swagger.ClassWithTypes
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.jackson.ModelResolver
import io.vertx.core.json.Json
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.GBP
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.TestIdentity
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class CustomModelConvertersV3Test {
  companion object {
    val DUMMY_BANK_A = TestIdentity(DUMMY_BANK_A_NAME, 40).party
    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      BraidCordaJacksonSwaggerInit.init()
    }
  }

  private val converter = ModelConverters().apply {
    addConverter(ModelResolver(Json.mapper))
    addConverter(CustomModelConverterV3())
  }

  @Test
  fun `should Correctly Model Amount`() {
    val models = converter.readAll(ClassWithTypes::class.java)
//    println(models)

    assertThat(models.toString(), models["Amount"], notNullValue())
    val properties = models["Amount"]?.properties

    assertThat(properties?.toString(), properties?.size, equalTo(4))
    assertThat(
      properties?.toString(),
      properties?.get("quantity")?.type,
      equalTo("integer")
    )
    assertThat(
      properties?.toString(),
      properties?.get("displayTokenSize")?.type,
      equalTo("number")
    )
    assertThat(properties?.toString(), properties?.get("token")?.type, equalTo("string"))
    assertThat(
      properties?.toString(),
      properties?.get("_tokenType")?.type,
      equalTo("string")
    )

  }

  @Test
  fun `should Correctly Model AmountCurrency and AmountString`() {
    val models = converter.readAll(ClassWithTypes::class.java)
//    println(models)

    val model = models["AmountCurrency"]
    assertThat(models.toString(), model, notNullValue())
    val properties = model?.properties

    assertThat(properties?.toString(), properties?.size, equalTo(3))
    assertThat(
      properties?.toString(),
      properties?.get("quantity")?.type,
      equalTo("integer")
    )
    assertThat(
      properties?.toString(),
      properties?.get("displayTokenSize")?.type,
      equalTo("number")
    )
    assertThat(properties?.toString(), properties?.get("token")?.type, equalTo("string"))
  }

//  @Test
//  fun `should Correctly Model OpaqueBytes as object`() {
//    val models = converter.readAll(ClassWithTypes::class.java)
////    println(models)
//
//    val model = models["ClassWithTypes"]
//    assertThat(models.toString(), model, notNullValue())
//
//    val properties = model?.properties
//    assertThat(properties?.keys, hasItem("bytes"))
//    assertThat(properties?.toString(), properties?.get("bytes")?.type, equalTo("string"))
//  }

  @Test
  fun `should Correctly Model SecureHash as string`() {
    val models = converter.readAll(ClassWithTypes::class.java)
//    println(models)

    val model = models["ClassWithTypes"]
    assertThat(models.toString(), model, notNullValue())

    val properties = model?.properties
    assertThat(properties?.keys, hasItem("hash"))
    assertThat(properties?.toString(), properties?.get("hash")?.type, equalTo("string"))
  }

  @Test
  fun `should Correctly Model Currency as string`() {
    val models = converter.readAll(ClassWithTypes::class.java)
//    println(models)

    val model = models["ClassWithTypes"]
    assertThat(models.toString(), model, notNullValue())

    val properties = model?.properties
    assertThat(properties?.keys, hasItem("currency"))
    assertThat(properties?.toString(), properties?.get("currency")?.type, equalTo("string"))
  }

  @Test
  fun `should Correctly Model Issued as string`() {
    val models = converter.readAll(ClassWithTypes::class.java)
    val model = models["IssuedCurrency"]
    assertThat(models.toString(), model, notNullValue())
    val properties = model?.properties
    val issuer = properties?.get("issuer")
    assertThat(properties?.toString(), issuer?.type, equalTo("object"))
    assertThat(properties?.toString(), properties?.get("product")?.type, equalTo("string"))
    // maps the issued type too
    // TODO: re-enable this when the new parser from https://gitlab.com/bluebank/braid/issues/183
//    assertThat(models.toString(), models.get("IssuedType"), notNullValue())
  }

  @Test
  @Ignore  // now serialize many parts
  fun `should Strip out SignedTransaction Exclusions`() {
    val models = converter.readAll(ClassWithTypes::class.java)

    val model = models["SignedTransaction"]
    assertThat(models.toString(), model, notNullValue())

    val properties = model?.properties
    assertThat(properties?.toString(), properties?.get("id"), nullValue())
    assertThat(properties?.toString(), properties?.get("inputs"), nullValue())
    assertThat(properties?.toString(), properties?.get("notary"), nullValue())
    assertThat(properties?.toString(), properties?.get("notaryChangeTx"), nullValue())
    assertThat(properties?.toString(), properties?.get("requiredSigningKeys"), nullValue())
    assertThat(properties?.toString(), properties?.get("sigs"), nullValue())
    assertThat(properties?.toString(), properties?.get("transaction"), nullValue())
    assertThat(properties?.toString(), properties?.get("tx"), nullValue())
    assertThat(properties?.toString(), properties?.get("txBits"), nullValue())

  }

  @Test
  fun `that OpaqueBytes can be serialised and deserialised`() {
    val expected = OpaqueBytes("someBytes".toByteArray())
    val encoded = Json.encode(expected)

    assertEquals(encoded, "{\"bytes\":\"c29tZUJ5dGVz\"}")
  }

  @Test
  fun `that Amount of String token can be serialised and deserialised`() {
    val expected = Amount(100, "GBP")
    val encoded = Json.encode(expected)

    assertEquals(
      encoded,
      "{\"quantity\":100,\"displayTokenSize\":1,\"token\":\"GBP\",\"_tokenType\":\"java.lang.String\"}"
    )
  }

  @Test
  fun `that Amount of Currency token can be serialised and deserialised`() {
    val expected = Amount(100, GBP)
    val encoded = Json.encode(expected)
    assertEquals(
      encoded,
      "{\"quantity\":100,\"displayTokenSize\":0.01,\"token\":\"GBP\"}"
    )
  }

//   @Test
//    fun `that SignedTransaction can be serialised and deserialised`() {
//        val expected = SignedTransaction(CoreTransaction(),listOf())
//        val encoded = Json.encode(expected)
//        assertEquals(encoded, "{\"quantity\":100,\"displayTokenSize\":0.01,\"token\":\"GBP\"}")
//    }

  @Test
  fun `that Amount of Issued Currency can be serialised and deserialised`() {
    val expected =
      Amount(100, Issued(PartyAndReference(DUMMY_BANK_A, OpaqueBytes.of(0x01)), GBP))
    val encoded = Json.encode(expected)
    assertEquals(
      encoded, "{\"quantity\":100," +
        "\"displayTokenSize\":0.01," +
      "\"token\":{\"issuer\":{\"party\":{\"name\":\"O=Bank A, L=London, C=GB\",\"owningKey\":\"GfHq2tTVk9z4eXgyUuofmR16H6j7srXt8BCyidKdrZL5JEwFqHgDSuiinbTE\"},\"reference\":{\"bytes\":\"AQ==\"}},\"product\":\"GBP\"}," +
        "\"_tokenType\":\"net.corda.core.contracts.Issued\"}"
    )
  }

  @Test
  fun `should Correctly Model Party as owning key string`() {

    val models = converter.readAll(ClassWithTypes::class.java)

    val properties = models["Party"]?.properties
    assertThat(properties?.size, equalTo(2))
    assertThat(properties?.keys, hasItem("name"))
    assertThat(properties?.toString(), properties?.get("name")?.type, equalTo("string"))

    assertThat(properties?.keys, hasItem("owningKey"))
    assertThat(
      properties?.toString(),
      properties?.get("owningKey")?.type,
      equalTo("string")
    )
  }

 @Test
  fun `should Serialize Classes`() {
   val encode = Json.encode(ClassWithTypes::class.java)
   assertThat(encode, equalTo("\"io.bluebank.braid.corda.swagger.ClassWithTypes\""))
 }

 @Test
  fun `should Correctly Model Class as string`() {

    val models = converter.readAll(ClassWithTypes::class.java)

   val properties = models["ClassWithTypes"]?.properties
    val classType = properties?.get("clazz")
    assertThat(properties?.toString(), classType, notNullValue())
    assertThat(properties?.toString(), classType?.type, equalTo("string"))
  }
}