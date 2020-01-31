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

import com.nhaarman.mockito_kotlin.mock
import io.bluebank.braid.corda.BraidCordaJacksonSwaggerInit
import io.bluebank.braid.corda.server.flow.FlowInitiator
import io.bluebank.braid.corda.services.FlowStarterAdapter
import io.bluebank.braid.cordapp.echo.flows.*
import io.bluebank.braid.cordapp.echo.states.*
import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.synth.ClassLogger
import io.bluebank.braid.core.synth.KParameterSynthetic
import io.bluebank.braid.core.synth.SynthesisOptions
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema
import io.vertx.core.Future
import io.vertx.core.eventbus.EventBus
import io.vertx.core.http.HttpMethod
import io.vertx.ext.auth.User
import net.corda.core.identity.Party
import org.junit.After
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import javax.ws.rs.core.MediaType
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFails

typealias ModelSchema = io.swagger.v3.oas.models.media.Schema<Any>

class FlowInitiatorTest {

  @After
  fun after() {
    SynthesisOptions.resetToDefaults()
  }

  companion object {
    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      BraidCordaJacksonSwaggerInit.init()
    }

    private val log = loggerFor<FlowInitiatorTest>()

    /*
    helper methods
     */

    fun DocsHandlerV3.getOpenAPI(): OpenAPI {
      val swaggerString = this.getSwaggerString()
      log.info(swaggerString)
      return Json.mapper().readValue(swaggerString, OpenAPI::class.java)
      // and/or this might work instead
      // return this.createOpenAPI()
    }

    fun OpenAPI.getComponentSchema(
      clazz: KClass<*>,
      // sometimes the key in the swagger string is unqualified (I don't know why)
      isQualified: Boolean = true
    ): ModelSchema {
      return this.components.schemas[if (isQualified) clazz.qualifiedName else clazz.simpleName]!!
    }

    fun ModelSchema.getPropertySchema(name: String): ModelSchema {
      return this.properties[name]!!
    }

    fun logClass(clazz: Class<*>) {
      val name = clazz.simpleName
      val result = ClassLogger.readClass(clazz)
      log.info("$name\r\n$result")
    }

    // this is a minimal/simplistic subset or emulation of what
    // DocsHandlerV3.addType(clazz).getOpenAPI().getComponentSchema(clazz)
    // is doing using Swagger.
    fun simplerOpenAPI(type: java.lang.reflect.Type): OpenAPI {
      val modelConverters = ModelConverters()
      val mutableModels = mutableMapOf<String, Schema<*>>()
      val resolveAsRef = false
      modelConverters.resolveAsResolvedSchema(
        AnnotatedType(type).resolveAsRef(resolveAsRef)
      )
        .also { schema ->
          val referencedSchemas = schema.referencedSchemas
          referencedSchemas.keys.forEach { schemaName ->
            mutableModels.compute(schemaName) { _, _ ->
              referencedSchemas[schemaName]
            }
          }
        }
      val models: Map<String, Schema<*>> = mutableModels
      val openAPI = OpenAPI()
        .apply {
          models.forEach { (name, schema) ->
            this.schema(name, schema)
          }
        }
      val swaggerString = Json.pretty().writeValueAsString(openAPI)
      log.info(swaggerString)
      return openAPI
    }

    /*
    these strings match hard-coded annotations in the cordapp classes used as test cases
     */

    private val expected = object {
      // strings in the EchoState classes
      val echoState = mapOf(
        "message" to "The message to be echoed",
        "numberOfEchos" to "The payload sent by the responder in the response flow",
        "sender" to "The initiator of the message",
        "responder" to "The responder which echoes the message"
      )
      // strings in the @Operation annotation of the flow-state's call method, which
      // we assert are copied to the callable function which is created by trampoline
      val callOperation = mapOf(
        "summary" to "Initiate an echo",
        "description" to "Send a message to a responder, which will echo it in a subsequent flow"
      )
      // string in annotations on the flow-states constructor parameters, which
      // we assert are copied to fields of the synthetic type
      val flowParameters = mapOf(
        // parameters the EchoSend.Initiator constructor
        "message" to "The message to be echoed",
        "responder" to "The counter-party which will respond with an echo",
        // parameters the EchoRespond.Initiator constructor
        "linearId" to "The initiated EchoState instance to be echoed",
        "numberOfEchos" to "The payload echoed with the message back to the initiator"
      )
    }
  }

  // contains the results of parsing a flow class which can then be asserted by test cases
  class FlowResults(
    val operation: Operation,
    val syntheticModelSchema: ModelSchema,
    val syntheticClass: Class<*>,
    val strategy: SynthesisOptions.ObjectStrategy,
    val openAPI: OpenAPI
  ) {

    companion object {
      fun get(
        flowClass1: KClass<*>,
        flowClass2: KClass<*>,
        flowClass3: KClass<*>
      ): Array<FlowResults> {
        return arrayOf(
          get(flowClass1, SynthesisOptions.ObjectStrategy.NONE),
          get(flowClass2, SynthesisOptions.ObjectStrategy.ALLOF),
          get(flowClass3, SynthesisOptions.ObjectStrategy.INLINE)
        )
      }

      fun get(
        flowClass: KClass<*>,
        strategy: SynthesisOptions.ObjectStrategy
      ): FlowResults {
        // set the strategy
        SynthesisOptions.currentStrategy = strategy

        // convert the class to a function
        fun getFlowStarter(user: User?): FlowStarterAdapter {
          throw UnsupportedOperationException("not implemented")
        }

        val eventBus: EventBus = mock<EventBus> {}
        val flowInitiator = FlowInitiator(::getFlowStarter, eventBus, true)
        val fn: KCallable<Future<Any?>> = flowInitiator.getInitiator(flowClass)

        // add the function to the document handler
        val path = "/cordapps/flows/${flowClass.java.name}"
        val docsHandler = DocsHandlerV3()
        // this emulates the way in which BraidCordaStandaloneServer
        // invokes RestMounter::post which
        // invokes RestMounter::bind which
        // invokes DocsHandler::add
        docsHandler.add("group", false, HttpMethod.POST, path, fn)

        // extract the documentation we just added for the path in question
        val openAPI: OpenAPI = docsHandler.getOpenAPI()
        val pathItem: PathItem = openAPI.paths[path]!!
        val operation: Operation = pathItem.post

        // and find the documentation for the synthetic type
        val ref =
          operation.requestBody.content[MediaType.APPLICATION_JSON]!!.schema.`$ref`
        val prefix = "#/components/schemas/"
        assert(ref.startsWith(prefix))
        val synthetic = openAPI.components.schemas[ref.substring(prefix.length)]!!

        // assert that the synthetic type name matches its name in the synthetic function
        // todo this is a weird way to get syntheticClass, because it uses a downcast to
        // an app-specific KParameterSynthetic -- what does Swagger do, to get this class?
        // it's the 3rd trampolined parameter, after the invocationId and user parameters.
        val syntheticClass: Class<*> = (fn.parameters[2] as KParameterSynthetic).clazz
        assertEquals(ref, prefix + syntheticClass.name)

        return FlowResults(operation, synthetic, syntheticClass, strategy, openAPI)
      }
    }
  }

  val sendFlowResults by lazy {
    FlowResults.get(
      EchoSend.Initiator::class,
      EchoSend1::class,
      EchoSend2::class
    )
  }

  val respondFlowResults by lazy {
    FlowResults.get(
      EchoRespond.Initiator::class,
      EchoRespond1::class,
      EchoRespond2::class
    )
  }

  /*
  test methods
   */

  /**
   * This is mostly only testing Swagger rather than testing Braid.
   * Its purpose is to experiment with how Swagger models an annotated type.
   * We learned from this how to annotate the synthetic flow type.
   */
  @Test
  fun `test using hard-coded annotations on the EchoState class and its variants`() {
    for (echoState in listOf(
      EchoState::class,
      EchoState1::class,
      EchoState2::class,
      EchoState5::class
    )) {
      logClass(echoState.java)
      val docsHandler = DocsHandlerV3()
      docsHandler.addType(echoState.java)
      val openAPI = docsHandler.getOpenAPI()
      val schema = openAPI.getComponentSchema(echoState)
      // assert that swagger sees the Schema annotation on the second parameter
      assertEquals(
        expected.echoState["numberOfEchos"],
        schema.getPropertySchema("numberOfEchos").description
      )
      // however it doesn't see the Parameter annotation on the second parameter
      assertEquals(
        null,
        schema.getPropertySchema("message").description
      )
      // and doesn't see the Schema annotation on the third parameter
      assertEquals(
        null,
        schema.getPropertySchema("sender").description
      )
      // the description from the fourth parameter now sadly exists in the referenced type
      when (echoState) {
        EchoState::class -> {
          // don't assert this, because it's Party rather than Party1,
          // and perhaps because it's Kotlin rather than Java
        }
        EchoState5::class -> {
          val party1Schema = openAPI.getComponentSchema(Party1::class)
          assertEquals(
            expected.echoState["sender"],
            party1Schema.description
          )
          val partySchema = openAPI.getComponentSchema(Party::class)
          assertEquals(
            expected.echoState["responder"],
            partySchema.description
          )
        }
        else -> {
          val party1Schema = openAPI.getComponentSchema(Party1::class)
          assertEquals(
            expected.echoState["responder"],
            party1Schema.description
          )
        }
      }
    }
  }

  /**
   * This is a follow-on or side-track from the other EchoState tests above
   */
  @Test
  fun `test EchoState using allOf annotations`() {
    for (echoState in listOf(
      EchoState3::class,
      EchoState4::class
    )) {
      logClass(echoState.java)

      // DocsHandlerV3 fails to load these classes, I don't know why
      // todo doesn't this seem to be a bug in Braid's model converter code or something?
      run {
        val docsHandler = DocsHandlerV3()
        docsHandler.addType(echoState.java)
        val openAPI = docsHandler.getOpenAPI()
        assert(openAPI.components.schemas.keys.all { it == "InvocationError" })
        assertFails {
          openAPI.getComponentSchema(echoState)
        }
      }

      // however we can get schema for the class if we don't use Braid's model converters
      val openAPI = simplerOpenAPI(echoState.java)
      val schema = openAPI.getComponentSchema(echoState, false)

      if (echoState == EchoState3::class) {
        // however the Party fields still aren't annotated
        assertEquals(
          null,
          schema.getPropertySchema("sender").description
        )

        // and the annotation is still associated with the type, not the field
        val party1Schema = openAPI.getComponentSchema(Party1::class, false)
        assertEquals(
          expected.echoState["responder"],
          party1Schema.description
        )
      }
    }
  }

  @Test
  fun `unit test the isParameterTypeAnnotatable function`() {
    val echoState = EchoState6::class
    logClass(echoState.java)
    val docsHandler = DocsHandlerV3()
    docsHandler.addType(echoState.java)
    val openAPI = docsHandler.getOpenAPI()
    val schema = openAPI.getComponentSchema(echoState)
    // prefer Java reflection instead of Kotlin reflection
    // i.e. echoState.java.fields instead of echoState.memberProperties
    echoState.java.fields.forEach {
      val name = it.name
      val type = it.type
      // test whether Swagger uses `$ref` or `type` for this property
      val isSimple: Boolean = schema.getPropertySchema(name).run {
        val isType: Boolean = this.type != null
        val isRef: Boolean = this.`$ref` != null
        assert(isType.xor(isRef))
        isType
      }
      assertEquals(isSimple, SynthesisOptions.isParameterTypeAnnotatable(type), name)
    }
  }

  @Test
  fun `test that an annotation is copied from the call method of the flow class`() {
    for (flowResults in sendFlowResults) {
      // assert that the description is properly carried over
      assertEquals(
        expected.callOperation["description"],
        flowResults.operation.description
      )
      // todo however the summary is null and I don't know why
      assertEquals(null, flowResults.operation.summary)
    }
  }

  /**
   * This documents that the synthetic class can't be visited by ClassReader
   * (and I don't know why now)
   */
  @Ignore
  @Test
  fun `visit the synthetic type using ClassReader`() {
    for (flowResults in sendFlowResults) {
      // todo using ClassLogger.readClass fails although ClassLogger.readBytes succeeds
      logClass(flowResults.syntheticClass)
    }
  }

  @Test
  fun `test that an annotation is copied from the constructor parameter of the flow class to the field of the synthetic payload type`() {
    for (flowResults in sendFlowResults) {
      // assert it works for simple parameter types like string
      assertEquals(
        expected.flowParameters["message"],
        flowResults.syntheticModelSchema.getPropertySchema("message").description
      )
      // unfortunately it fails for complex parameter types like Party
      assertEquals(
        when (flowResults.strategy) {
          // unfortunately this strategy must fail to annotate complex parameter types like Party
          SynthesisOptions.ObjectStrategy.NONE -> null
          else -> expected.flowParameters["responder"]
        },
        flowResults.syntheticModelSchema.getPropertySchema("responder").description
      )
      // but assert that the annotation is not applied to the Party type in any scenario
      assertEquals(
//        when (flowResults.strategy) {
//          // unfortunately this strategy still pollutes the Party type even though it's inline
//          // if this strategy were wanted we might be able to get it to work by
//          // creating a different copy of the Schema to use inline
//          SynthesisOptions.ObjectStrategy.INLINE -> expected.flowParameters["responder"]
//          SynthesisOptions.ObjectStrategy.ALLOF -> expected.flowParameters["responder"]
//          // successful because AsmUtils filters parameter types via the isSimpleType filter
//          // or because ObjectStrategy.ALLOF puts the annotation on a sufficiently-different schema
//          else -> null
//        },
        null,
        flowResults.openAPI.getComponentSchema(Party::class).description,
        flowResults.strategy.toString()
      )
    }
  }

  // This test works properly if it's run by itself,
  // but in an earlier version of the test suite,
  // running this test would cause a problem in a later test in the suite,
  // because this tests adds a description to the Party type,
  // even though this test tries to use a different FlowResults instance.
  // I guess that implies that the Swagger model for the Party type is cached
  // somewhere else, though I don't know where.
  // If that problem recurs then you might want to @Ignore this test.
  // @Ignore
  @Test
  fun `test that the parameter type filter is necessary`() {
    SynthesisOptions.isParameterTypeFilterEnabled = false
    val flowResults =
      FlowResults.get(EchoSendExtra::class, SynthesisOptions.ObjectStrategy.NONE)
    assertEquals(
      "The counter-party which will respond with an echo",
      flowResults.openAPI.getComponentSchema(Party::class).description
    )
  }

  @Test
  fun `test that Parameter annotation is converted to Schema annotation`() {
    for (flowResults in respondFlowResults) {
      assertEquals(
        expected.flowParameters["numberOfEchos"],
        flowResults.syntheticModelSchema.getPropertySchema("numberOfEchos").description
      )
    }
  }

  @Test
  fun `test exotic parameter types`() {
    val flowClass = EchoFlowExotic::class
    FlowResults.get(flowClass, SynthesisOptions.ObjectStrategy.ALLOF)
  }
}
