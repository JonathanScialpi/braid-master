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

import io.bluebank.braid.corda.swagger.v3.*
import io.bluebank.braid.core.synth.SyntheticModelConverter
import io.bluebank.braid.core.synth.SyntheticRefOption
import io.bluebank.braid.core.synth.TypeNames
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContextImpl
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.core.util.Json
import io.vertx.core.buffer.Buffer
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import org.junit.Test
import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/*
 * This file contains tests of SyntheticModelConverter
 * Ideally this would be in braid-core like SyntheticModelConverter is
 * but it includes the io.bluebank.braid.corda.swagger converters in its testing.
 *
 * It could be moved to braid-core if or when
 * https://gitlab.com/bluebank/braid/merge_requests/208
 * is landed or
 * https://gitlab.com/bluebank/braid/issues/172
 * is reimplemented.
 */

/*
 * These classes emulate diverse synthetic objects corresponding to flow parameters
 */

class Foo {}

class Foo1 {
  val bar: Int = 0
}

class Foo2 {
  val bar: Int? = null
}

enum class E { CAT, DOG }

class Foo3 {
  val bar: E = E.CAT
}

private fun generatePublicKey(): PublicKey {
  val publicKey = Crypto.generateKeyPair(Crypto.RSA_SHA256).public
  return publicKey
}

private val cordaX500Name = CordaX500Name("Foo", "", "US")
private val party = Party(cordaX500Name, generatePublicKey())
private val currency = Currency.getInstance(Locale.CHINA)
private val issued = Issued(PartyAndReference(party, OpaqueBytes.of(0x01)), currency)

class Foo4 {
  val bar: Party = party
}

class Foo5 {
  val bar: Set<Party> = emptySet()
}

class Foo6 {
  val bar: Array<Party> = emptyArray()
}

class Foo7 {
  val bar: Map<String, Party> = emptyMap()
}

class Foo8 {
  val bar: Currency = currency
}

class Foo9 {
  // try several of the types defined in CustomModelConverterV3 which are special cases
  val bar1 = Buffer.buffer()
  val bar2 = ByteArray(42)
  val bar3 = ByteBuffer.allocate(42)
  val bar4 = Buffer.buffer("hello").byteBuf
  val bar5 = Party::class.java
  val bar6: SecureHash = SecureHash.sha256("hello")
  val bar7 = cordaX500Name

  val bar9 = issued
}

val kclasses = listOf(
  Foo::class,
  Foo1::class,
  Foo2::class,
  Foo3::class,
  Foo4::class,
  Foo5::class,
  Foo6::class,
  Foo7::class,
  Foo8::class,
  Foo9::class
)

class SyntheticModelConverterTest {

  companion object {
    // this list is copy-and-pasted from ModelContextV3
    // which could be refactored so that this isn't copy-and-pasted
    fun createConverters(): List<ModelConverter> {
      return listOf(
        QualifiedTypeNameConverter(io.vertx.core.json.Json.mapper),
        JSR310ModelConverterV3(),
        MixinModelConverterV3(io.vertx.core.json.Json.mapper),
        SuperClassModelConverterV3(),
        ComposedSchemaFixV3(),
        CustomModelConverterV3()
      )
    }
  }

  /**
   * Verify that schema returned by SyntheticModelConverter.resolveSchema
   * matches the schema returned by
   */
  @Test
  fun `assert that resolved models are identical`() {
    // a vanilla resolver
    // excluding SyntheticModelConverter (which is the object under test here)
    // but including the other custom converters defined in ModelContextV3
    val modelResolver = ModelConverters().apply {
      createConverters().forEach { this.addConverter(it) }
    }

    // this gets a ModelConverterContext instance
    // like the one which is created inside the ModelConverters implementation
    // with an additional SyntheticModelConverter instance at the front
    // like it would be when it's used inside ModelContextV3
    val otherConverters = createConverters()
    val typeNames = otherConverters.filterIsInstance<TypeNames>().single()
    val syntheticModelConverter = SyntheticModelConverter(typeNames)
    val context = ModelConverterContextImpl(
      // addConverter puts the most-recently added at the front of the list
      listOf(syntheticModelConverter) +
        otherConverters.reversed() +
        listOf(ModelResolver(Json.mapper()))
    )

    for (kclass in kclasses) {
      // vanilla resolution
      val resultMap = modelResolver.read(kclass.java)
      val result1 = resultMap[kclass.qualifiedName]
      assertNotNull(result1)
      // custom resolution
      val opt = SyntheticRefOption.REF
      val result2 = syntheticModelConverter.resolveSchema(kclass, context, opt)
      assertNotNull(result2)
      // assert equality
      result1!!.required = result2.required // HACK
      assertEquals(Json.pretty(result1), Json.pretty(result2), kclass.simpleName)
    }
  }
}