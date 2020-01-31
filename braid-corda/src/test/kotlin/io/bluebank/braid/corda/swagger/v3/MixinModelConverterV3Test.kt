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

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.MINIMAL_CLASS
import io.bluebank.braid.corda.serialisation.mixin.QueryCriteriaMixin
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import net.corda.core.node.services.vault.QueryCriteria
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasKey
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MixinModelConverterV3Test {
   var schemas: MutableMap<String, Schema<Any>>? = null

  @Before
  fun setUp() {
    Json.mapper.addMixIn(Pet::class.java, PetMixin::class.java)

     schemas = ModelConverters()
        .apply {
          addConverter(ModelResolver(Json.mapper))
          addConverter(MixinModelConverterV3(Json.mapper))
        }
        .readAll(Pet::class.java)

  }

  @Test
  fun `should have discriminator for QueryCriteria`() {
    Json.mapper.addMixIn(QueryCriteria::class.java, QueryCriteriaMixin::class.java)

    val schemas = ModelConverters()
        .apply {
          addConverter(ModelResolver(Json.mapper))
          addConverter(MixinModelConverterV3(Json.mapper))
        }
        .readAll(QueryCriteria.AndComposition::class.java)

    assertThat(schemas, hasKey("QueryCriteria"))
    assertThat(schemas.get("QueryCriteria")?.discriminator, notNullValue())

  }

  @Test
  fun `should serialize Dog`() {
    val dog = Dog(true, 4)
    val json = Json.encode(dog)

    val roundTripDog = Json.decodeValue(json, Pet::class.java)
    assertThat("expecting dog", roundTripDog, `is`(dog as Pet))
  }

  @Test
  fun `should round trip Dog`() {
    val dog = Dog(true, 4)
    val json = Json.encode(dog)
    val parsed = JsonObject(json)
    val expected = JsonObject()
      .put("@class", ".MixinModelConverterV3Test${'$'}Dog")
      .put("noisy", true)
      .put("legs", 4)
    assertEquals(expected, parsed)
//    assertThat(json, equalTo("""{"@class":".MiximModelConverterV3Test${'$'}Dog","noisy":true,"legs":4}"""))
  }

  @Test
  fun `should generate dog with correct all of reference`() {
    val dog = schemas?.get("Dog") as ComposedSchema? ?: error("did not find Dog")
    assertThat(dog.allOf[0]?.`$ref`, containsString("components"))
  }

  @Test
  fun `should generate subtypes`() {
    assertThat(schemas, hasKey("Dog"))
    assertThat(schemas, hasKey("Cat"))
  }

  @Test
  fun `should generate Base type`() {
    assertThat(schemas, hasKey("Pet"))
  }

  @Test
  fun `should exclude mixin`() {
    assertFalse(schemas?.containsKey("PetMixin") ?: false)
  }

  @Test
  fun `should generate discriminator on Pet`() {

// AnnotatedType(Pet::class.java).ctxAnnotations(PetMixin::class.annotations.toTypedArray())

    assertThat(schemas, hasKey("Pet"))
    assertThat(schemas?.get("Pet")?.discriminator, notNullValue())
  }

  @JsonSubTypes(
    Type(value = Dog::class, name = "Dog"),
    Type(value = Cat::class, name = "Cat")
  )
  @JsonTypeInfo(
    use = MINIMAL_CLASS,
    include = PROPERTY,
    property = "@class",
    defaultImpl = Pet::class
  )
  @io.swagger.v3.oas.annotations.media.Schema(
      type = "object",
      title = "Pet",
      discriminatorProperty = "@class",
      discriminatorMapping = [
        DiscriminatorMapping(value = ".MiximModelConverterV3Test${'$'}Dog", schema = Dog::class),
        DiscriminatorMapping(value = ".MiximModelConverterV3Test${'$'}Cat", schema = Cat::class)
      ],
      subTypes = [Dog::class, Cat::class]
  )
  abstract class PetMixin{
  }

  abstract class Pet {
  }

  data class Dog(val noisy: Boolean = false, val legs: Int = 4) : Pet() {
  }

  data class Cat(val friendly: Boolean = true, val legs: Int = 4) : Pet() {
  }

}