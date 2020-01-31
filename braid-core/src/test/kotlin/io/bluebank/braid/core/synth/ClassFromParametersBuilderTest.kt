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
package io.bluebank.braid.core.synth

import io.bluebank.braid.core.json.BraidJacksonInit
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.media.Schema
import io.vertx.core.json.Json
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.Assert
import org.junit.Test
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import javax.validation.constraints.NotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// equivalent to Corda's Amount - this module doesn't reference Corda directly hence this type
data class Amount<T>(val quantity: Long, val of: T)

class Simple(val amount: Long)
class MoreComplex(val name: String, val age: Int, val amount: Amount<String>)
class NestedGenerics(val map: Map<String, List<String>>)

class ClassFromParametersBuilderTest {
  companion object {
    init {
      BraidJacksonInit.init()
    }

    fun buildClass(javaClass: Class<*>): Class<*> {
      val constructor: Constructor<*> = javaClass.constructors.first()
      return ClassFromParametersBuilder.acquireClass(
        constructor.parameters,
        ClassLoader.getSystemClassLoader(),
        constructor.declaringClass.payloadClassName()
      )
    }
  }

  private val typesForSynthesis = listOf(
    Simple::class,
    MoreComplex::class,
    NestedGenerics::class
  )
  private val sampleValues = listOf(
    Simple(1000L),
    MoreComplex(name = "fred", age = 32, amount = Amount(quantity = 1000L, of = "USD")),
    NestedGenerics(
      map = mapOf(
        "names" to listOf("Jim", "Ian", "Tim", "Fuzz"),
        "locations" to listOf("Cambridge", "New York", "Bahamas", "London")
      )
    )
  )

  @Test
  fun `that we can synthesize payload types and deserialize`() {
    sampleValues.forEach { assertSynthesizedTypeCanDeserialize(it) }
  }

  @Test
  fun `that swagger will accept the synthetic payload types`() {
    val modelMap = sampleValues.fold<Any, Map<String, Schema<*>>>(mapOf()) { acc, input ->
      val payloadClass = buildClass(input.javaClass)
      acc + ModelConverters.getInstance().readAll(payloadClass)
    }
    typesForSynthesis.forEach {
      assertTrue(
        modelMap.containsKey(Class.forName(it.java.payloadClassName()).simpleName),
        it.java.payloadClassName()
      )
    }

//    assertTrue(modelMap.containsKey(Amount::class.java.simpleName))
    assertTrue(modelMap.containsKey("AmountString")) // contains the name for the specialised Amount class
  }

  private fun <T : Any> assertFieldsAreEqual(input: T, parsed: Any) {
    val modifiersField = Field::class.java.getDeclaredField("modifiers")
    modifiersField.isAccessible = true
    input.javaClass.declaredFields.zip(parsed.javaClass.declaredFields)
      .forEach { (inputField, parsedField) ->
        modifiersField.setInt(inputField, inputField.modifiers or Modifier.PUBLIC)
        assertEquals(inputField.get(input), parsedField.get(parsed))
      }
  }

  private fun <T : Any> assertSynthesizedTypeCanDeserialize(input: T) {
    val payloadClass = buildClass(input.javaClass)
    val json = Json.encodePrettily(input)
    val parsed = Json.decodeValue(json, payloadClass)
    assertFieldsAreEqual(input, parsed)
  }

  @Test
  fun `that synthesized types have annotation NotNull`() {
    sampleValues.forEach { assertSynthesizedTypeHasNonNullAnnotations(it) }
  }

  private fun <T : Any> assertSynthesizedTypeHasNonNullAnnotations(input: T) {
    val payloadClass = buildClass(input.javaClass)
    payloadClass.fields.forEach {
      val notNull = it.getAnnotation(NotNull::class.java)
      Assert.assertThat(notNull, notNullValue())
    }
  }
}

