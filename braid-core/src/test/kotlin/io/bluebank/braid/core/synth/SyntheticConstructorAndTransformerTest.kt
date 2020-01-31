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

import io.bluebank.braid.core.logging.loggerFor
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.vertx.core.json.Json
import io.vertx.ext.auth.User
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import javax.ws.rs.core.Context
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProgressTracker {
  companion object {
    private val log = loggerFor<ProgressTracker>()
  }

  fun ping() {
    log.trace("ping")
  }
}

interface FlowLogic<T> {
  fun call(): T
}

class FooFlow(
  private val i: Int,
  private val l: Long = 42,
  private val progressTracker: ProgressTracker
) : FlowLogic<Long> {

  override fun call(): Long {
    progressTracker.ping()
    return i.toLong() + l
  }
}

class UnboundParameterFooFlow<OldState>(
  private val i: Int,
  private val l: Map<String, OldState>
) : FlowLogic<OldState> {

  override fun call(): OldState {
    return l.get("key")!!
  }
}

class UnboundFooFlow<OldState>(
  private val i: Int,
  private val l: OldState
) : FlowLogic<OldState> {

  override fun call(): OldState {
    return l
  }
}

class BadlyAnnotatedFlow(
  private val i: Int,
  @Suppress("UNUSED_PARAMETER") @Context user: User
) : FlowLogic<Int> {

  override fun call(): Int {
    return i
  }
}

// Deprecated is one of only very few annotations -- including no swagger annotations --
// that can be applied to a constructor
class AnnotatedConstructorFlow
@Deprecated("Deprecated is an annotation that can be applied to a constructor")
constructor(
  private val l: Long
) : FlowLogic<Long> {

  override fun call(): Long {
    return l
  }
}

// So we make it possible for a class annotation such as this one, to be applied using
// additionalAnnotations to the function which trampoline creates from the constructor
class AnnotatedClassFlow(
  private val l: Long
) : FlowLogic<Long> {

  @Operation(description = "Some description of this flow class")
  override fun call(): Long {
    return l
  }
}

class AnnotatedParameterFlow(
  @Parameter(
    description = "the X500 name for the node",
    example = "O=PartyB, L=New York, C=US",
    // tests nested annotations and array values
    examples = arrayOf(
      ExampleObject(
        value = "O=PartyA, L=New York, C=US"
      ),
      ExampleObject(
        value = "O=PartyC, L=New York, C=US"
      )
    ),
    // tests a primitive (non-String)
    required = true
  )
  // tests an enum
  @Animal(AnimalType.Dog)
  private val name: String,
  // tests a class
  @SeeAlso(String::class)
  private val l: Long
) : FlowLogic<Long> {

  override fun call(): Long {
    return l + name.length
  }
}

class SwaggeredFlow(
  @Parameter(description = "The user name for login", required = true)
  private val name: String,
  @Schema(
    description = "pet status in the store",
    allowableValues = ["available", "pending", "sold"]
  )
  private val status: String
) : FlowLogic<String> {

  // this is a textbook example of an @Operation i.e.
  // https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations#operation
  @Operation(
    summary = "Get user by user name",
    responses = [ApiResponse(
      description = "The user",
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(
            implementation = User::class
          )
        )
      ]
    ),
      ApiResponse(responseCode = "400", description = "User not found")
    ]
  )
  override fun call(): String {
    return "$name is $status"
  }
}

// this is a cheap way to do a deep compare of annotations' fields
// because toString() seems to include all the field values
fun areAnnotationsEqual(lhs: Annotation, rhs: Annotation): Boolean {
  return lhs.toString() == rhs.toString()
}

// this is like areAnnotationsEqual except it tests
// a @Parameter converted to a @Schema
fun areAnnotationsSimilar(lhs: Schema, rhs: Parameter): Boolean {
  return lhs.description == rhs.description
}

class SyntheticConstructorAndTransformerTest {

  @After
  fun after() {
    // some tests disable the filter, to test using non-swagger annotations
    SynthesisOptions.resetToDefaults()
  }

  @Before
  fun before() {
    // this is testing ObjectStrategy.NONE
    // other strategies are tested in FlowInitiatorTest
    SynthesisOptions.currentStrategy = SynthesisOptions.ObjectStrategy.NONE
  }

  /**
   * some tests disable the filter, to test using non-swagger annotations
   */
  private fun enableAnyParameterAnnotation() {
    SynthesisOptions.isParameterAnnotation = SynthesisOptions::unfiltered
  }

  private fun enableAnyMethodAnnotation() {
    SynthesisOptions.isMethodAnnotation = SynthesisOptions::unfiltered
  }

  @Test
  fun `that we can create a wrapper around a flow and inject context parameters and custom transformer`() {
    val boundTypes = createBoundParameterTypes()
    val constructor = FooFlow::class.java.preferredConstructor()
    val fn = trampoline(constructor, boundTypes, "MyPayloadName") {
      // do what you want here ...
      // e.g. call the flow directly
      // obviously, we will be invoking the flow via an interface to CordaRPCOps or ServiceHub
      // and return a Future
      // it.call()
      constructor.newInstance(*it).call()
      // println(it)
    }

    val json = """
    {
      "i": 100,
      "l": 1000
    }
  """

    val payload = Json.prettyMapper.decodeValue(json, fn)
    val result = fn.call(payload)
    assertEquals(1100L, result)
  }

  private fun createBoundParameterTypes(): Map<Class<*>, Any> {
    return mapOf<Class<*>, Any>(ProgressTracker::class.java to ProgressTracker())
  }

  @Test
  fun `should fail if the user-defined flow has a Context annotation`() {
    val constructor = BadlyAnnotatedFlow::class.java.preferredConstructor()
    val error = assertFails {
      trampoline(constructor, emptyMap(), "MyUnboundPayloadName") {
        constructor.newInstance(*it).call()
      }
    }
    assert(error.message!!.contains("Context"))
  }

  @Test
  fun shouldNotIncludeSyntheticConstructors() {
    val constructor = FooFlow::class.java.preferredConstructor()
    assertThat(constructor.isSynthetic, `is`(false))
  }

  // This seems possible to have unbounded types.
  @Test
  fun `that we treat unbound parameters as their base class`() {
    val constructor = UnboundFooFlow::class.java.preferredConstructor()
    val fn = trampoline(constructor, emptyMap(), "MyUnboundPayloadName") {
      constructor.newInstance(*it).call()
    }

    val json = """
    {                           
      "i": 100,
      "l": { "key":"value" }
    }
  """

    val payload = Json.prettyMapper.decodeValue(json, fn)
    val result = fn.call(payload)
    assertEquals(mapOf("key" to "value"), result)
  }

  @Test
  fun `that constructor annotations are preserved`() {
    enableAnyMethodAnnotation()
    val constructor = AnnotatedConstructorFlow::class.java.preferredConstructor()
    val fn = trampoline(constructor, emptyMap()) {
      constructor.newInstance(*it).call()
    }

    assertEquals(fn.annotations.size, 1)
    assertTrue(fn.annotations[0] is Deprecated)
  }

  @Test
  fun `that class annotations can be preserved`() {
    val constructor = AnnotatedClassFlow::class.java.preferredConstructor()
    val additionalAnnotations = getFlowMethodAnnotations(AnnotatedClassFlow::class)
    val fn = trampoline(
      constructor = constructor,
      boundTypes = emptyMap(),
      additionalAnnotations = additionalAnnotations
    ) {
      constructor.newInstance(*it).call()
    }

    assertEquals(fn.annotations.size, 1)
    assertTrue(fn.annotations[0] is Operation)
    assertEquals(
      (fn.annotations[0] as Operation).description,
      "Some description of this flow class"
    )
  }

  @Test
  fun `that constructor parameters can be annotated`() {
    enableAnyParameterAnnotation()
    val constructor = AnnotatedParameterFlow::class.java.preferredConstructor()
    val fn = trampoline(constructor, emptyMap()) {
      constructor.newInstance(*it).call()
    }

    assertEquals(fn.parameters.size, 1)
    val parameter = fn.parameters[0];
    val fields = (parameter as KParameterSynthetic).clazz.fields

    assertEquals(fields.size, 2)

    val nameField = fields[0]
    assertEquals(nameField.name, "name")
    fun assertExamples(examples: Array<ExampleObject>): Boolean {
      return (examples.size == 2) &&
        examples[0].value == "O=PartyA, L=New York, C=US"
    }

    fun assertParameter(it: Parameter): Boolean {
      return it.description == "the X500 name for the node" &&
        it.example == "O=PartyB, L=New York, C=US" &&
        assertExamples(it.examples) &&
        it.required
    }

    fun assertSchema(it: Schema): Boolean {
      return it.description == "the X500 name for the node" &&
        it.example == "O=PartyB, L=New York, C=US"
      // Schema doesn't support examples
      // && assertExamples(it.examples)
    }

    fun assertAnimal(it: Animal): Boolean {
      return it.value == AnimalType.Dog
    }
    // this doesn't happen because ClassWriter.addField converts Parameter to Schema
//    assertTrue(
//      nameField.annotations.any { it is Parameter && assertParameter(it) },
//      "Expected to find Parameter annotation"
//    )
    assertTrue(
      nameField.annotations.any { it is Schema && assertSchema(it) },
      "Expected to find Schema annotation"
    )
    assertTrue(
      nameField.annotations.any { it is Animal && assertAnimal(it) },
      "Expected to find Animal annotation"
    )

    fun assertSeeAlso(it: SeeAlso): Boolean {
      return it.value == String::class
    }

    val secondField = fields[1]
    assertTrue(
      secondField.annotations.any { it is SeeAlso && assertSeeAlso(it) },
      "Expected to find SeeAlso annotation"
    )

    val json = """
    {                           
      "name": "foo",
      "l": 2
    }
  """

    val payload = Json.prettyMapper.decodeValue(json, fn)
    val result = fn.call(payload)
    assertEquals(5, result)
  }

  @Test
  fun `integration test with swagger parameters and annotated call function`() {
    // read the annotations which decorate the call method
    val additionalAnnotations = getFlowMethodAnnotations(SwaggeredFlow::class)
    assertNotNull(
      additionalAnnotations.find { it is Operation },
      "Expected Operation annotation"
    )

    val constructor = SwaggeredFlow::class.java.preferredConstructor()
    val fn = trampoline(
      constructor = constructor,
      boundTypes = emptyMap(),
      additionalAnnotations = additionalAnnotations
    ) {
      constructor.newInstance(*it).call()
    }

    assertEquals(fn.parameters.size, 1)
    val parameter = fn.parameters[0];
    val fields = (parameter as KParameterSynthetic).clazz.fields

    assertEquals(fields.size, 2)

    assertTrue(
      areAnnotationsSimilar(
        fields[0].annotations.find { it is Schema }!! as Schema,
        constructor.parameters[0].annotations.find { it is Parameter }!! as Parameter
      ), "Expected matching Parameter annotation"
    )

    assertTrue(
      areAnnotationsEqual(
        fields[1].annotations.find { it is Schema }!!,
        constructor.parameters[1].annotations.find { it is Schema }!!
      ), "Expected matching Schema annotation"
    )

    assertTrue(
      areAnnotationsEqual(
        additionalAnnotations.find { it is Operation }!!,
        fn.annotations.find { it is Operation }!!
      ), "Expected matching Operation annotation"
    )
  }

  // not sure if this is possible. It is derived from the failure to synthesise
  // ContractUpgradeFlow.Initiate
  @Test
  @Ignore
  fun `that we treat parameterised types from their parent class`() {
    val constructor = UnboundParameterFooFlow::class.java.preferredConstructor()
    val fn = trampoline(constructor, emptyMap(), "MyUnboundParameterPayloadName") {
      constructor.newInstance(*it).call()
    }

    val json = """
    {
      "i": 100,
      "l": { "key":"value" }
    }
  """

    val payload = Json.prettyMapper.decodeValue(json, fn)
    val result = fn.call(payload)
    assertEquals("value", result)
  }

}