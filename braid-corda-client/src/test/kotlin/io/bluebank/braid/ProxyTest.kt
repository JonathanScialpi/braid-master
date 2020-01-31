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
package io.bluebank.braid

import io.bluebank.braid.client.BraidClient
import io.bluebank.braid.client.BraidClientConfig
import io.bluebank.braid.server.ComplexObject
import io.bluebank.braid.server.JsonRPCServer
import io.bluebank.braid.server.JsonRPCServerBuilder.Companion.createServerBuilder
import io.bluebank.braid.server.MyService
import io.bluebank.braid.server.MyServiceImpl
import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.net.ServerSocket
import java.net.URI

@RunWith(VertxUnitRunner::class)
class ProxyTest {

  private val vertx = Vertx.vertx()
  private val clientVertx = Vertx.vertx()
  private val port = getFreePort()
  private lateinit var rpcServer: JsonRPCServer
  private lateinit var braidClient: BraidClient

  private lateinit var myService: MyService

  @Before
  fun before(context: TestContext) {
    val async = context.async()
    rpcServer = createServerBuilder()
      .withVertx(vertx)
      .withService(MyServiceImpl(vertx))
      .withPort(port)
      .build()

    rpcServer.start {
      val serviceURI = URI("https://localhost:$port${rpcServer.rootPath}my-service/braid")
      braidClient = BraidClient.createClient(
        BraidClientConfig(
          serviceURI = serviceURI,
          trustAll = true,
          verifyHost = false
        ), clientVertx
      )

      try {
        myService = braidClient.bind(MyService::class.java)
        async.complete()
      } catch (ex: Throwable) {
//        println(ex.message)
        throw ex
      }
    }

    async.awaitSuccess()
  }

  @After
  fun after(context: TestContext) {
    vertx.close(context.asyncAssertSuccess())
    braidClient.close()
  }

  @Test
  fun `should be able to add two numbers together`() {
    val result = myService.add(1.0, 2.0)
    assertEquals(0, braidClient.activeRequestsCount())
    assertEquals(3.0, result, 0.0001)
  }

  @Test
  fun `sending a request to a no args function finds the function`() {
    val result = myService.noArgs()
    assertEquals(5, result)
  }

  @Test
  fun `sending a request to a client with multiple functions with the same name and number of parameters finds the correct function - long input`() {
    val result = myService.functionWithTheSameNameAndNumberOfParameters(200L, "100.123")
    assertEquals(
      5,
      result
    ) // will always be a perfect match with the int overload. sorry, that's javascript
  }

  @Test
  fun `sending a request to a client with multiple functions with the same name and number of parameters finds the correct function - int input`() {
    // long version always chosen over int
    val result = myService.functionWithTheSameNameAndNumberOfParameters(200, "account id")
    assertEquals(5, result)
  }

  @Test
  fun `sending a request to a client with multiple functions with the same name and number of parameters finds the correct function (float version) - float input`() {
    // double always chosen over float
    val result =
      myService.functionWithTheSameNameAndNumberOfParameters(200.1234F, "100.123")
    assertEquals(7, result) // float binding is nearer
  }

  @Test
  fun `sending a request to a client with multiple functions with the same name and number of parameters finds the correct function - double input`() {
    val result =
      myService.functionWithTheSameNameAndNumberOfParameters(200.1234, "100.123")
    assertEquals(7, result)
  }

  @Test
  fun `sending a request to a client with multiple functions with the same name and number of parameters finds the correct function - complex object input`() {
    val result = myService.functionWithTheSameNameAndNumberOfParameters(
      ComplexObject("1", 2, 3.0),
      "100.123"
    )
    assertEquals(8, result)
  }

  @Test
  fun `sending a request to a client with multiple functions with the same name and number of parameters finds the correct function - null complex object input`() {
    val result =
      myService.functionWithTheSameNameAndNumberOfParameters(null, "account id")
    assertEquals(8, result)
  }

  @Test
  fun `sending a request to a client with multiple functions with the same name and number of parameters finds the correct function - list input`() {
    val result = myService.functionWithTheSameNameAndNumberOfParameters(
      listOf("a", "b", "c"),
      "100.123"
    )
    assertEquals(9, result)
  }

  @Test
  fun `sending a request to a client with multiple functions with the same name and number of parameters finds the correct function (list version) - array input`() {
    // list always chosen over array
    val result = myService.functionWithTheSameNameAndNumberOfParameters(
      arrayOf("a", "b", "c"),
      "100.123"
    )
    assertEquals(9, result)
  }

  @Test
  fun `sending a request to a client with multiple functions with the same name and number of parameters finds the correct function - string input`() {
    val result = myService.functionWithTheSameNameAndNumberOfParameters(
      "not a number",
      "My Netflix account"
    )
    assertEquals(1, result)
  }

  @Test
  fun `eager lazy seq`() {
    (0..10).toList()
      .asSequence()
      .map {
        //        println(it)
        it
      }.firstOrNull()

  }

  @Test
  fun `sending a request to a client with multiple functions with the same name and number of parameters finds the correct function (BigDecimal version) - big decimal input`() {
    // string always chosen over big decimal
    val functionWithABigDecimalParameterResult =
      myService.functionWithTheSameNameAndNumberOfParameters(
        BigDecimal("200.12345"),
        "My Netflix account"
      )
    assertEquals(2, functionWithABigDecimalParameterResult)
    val functionWithTwoBigDecimalParametersResult =
      myService.functionWithTheSameNameAndNumberOfParameters(
        BigDecimal("200.12345"),
        BigDecimal("200.12345")
      )
    assertEquals(3, functionWithTwoBigDecimalParametersResult)
    val functionWithBigDecimalAndStringNumberParametersResult =
      myService.functionWithTheSameNameAndNumberOfParameters(
        BigDecimal("200.12345"),
        "200.12345"
      )
    assertEquals(3, functionWithBigDecimalAndStringNumberParametersResult)
  }

  @Test
  fun `sending a request with a single parameter finds the function - string input`() {
    val result = myService.functionWithTheSameNameAndASingleParameter("A string")
    assertEquals(12, result)
  }

  @Test
  fun `sending a request with a single parameter finds the function - null string input`() {
    val result = myService.functionWithTheSameNameAndASingleParameter(null)
    assertEquals(12, result)
  }

  @Test
  fun `sending a request with a single parameter finds the function - long input`() {
    val result = myService.functionWithTheSameNameAndASingleParameter(11L)
    assertEquals(14, result) // perfect match with the int overload - sorry, javascript
  }

  @Test
  fun `sending a request with a single parameter finds the function (int version) - int input`() {
    // long version always chosen over int
    val result = myService.functionWithTheSameNameAndASingleParameter(12)
    assertEquals(14, result) // perfect match with the int overload
  }

  @Test
  fun `sending a request with a single parameter finds the function - double input`() {
    val result = myService.functionWithTheSameNameAndASingleParameter(12.34)
    assertEquals(15, result)
  }

  @Test
  fun `sending a request with a single parameter finds the function (double version) - float input`() {
    // double always chosen over float
    val result = myService.functionWithTheSameNameAndASingleParameter(12.34F)
    assertEquals(15, result)
  }

  @Test
  fun `sending a request with a single parameter finds the function - complex object input`() {
    val result =
      myService.functionWithTheSameNameAndASingleParameter(ComplexObject("hi", 1, 2.0))
    assertEquals(17, result)
  }

  @Test
  fun `sending a request with a single parameter finds the function - list input`() {
    val result = myService.functionWithTheSameNameAndASingleParameter(
      listOf(
        "i",
        "am",
        "a",
        "string"
      )
    )
    assertEquals(18, result)
  }

  @Test
  fun `sending a request with a single parameter finds the function - array input`() {
    val result = myService.functionWithTheSameNameAndASingleParameter(
      arrayOf(
        "i",
        "am",
        "a",
        "string"
      )
    )
    assertEquals(18, result)
  }

  @Test
  fun `sending a request to a big decimal function finds the function`() {
    val result = myService.functionWithBigDecimalParameters(
      BigDecimal("200.12345"),
      BigDecimal("200.12345")
    )
    assertEquals(21, result)
  }

  @Test
  fun `sending a complex type to an overloaded method that includes Map should always choose the map`() {
    val result = myService.functionWithComplexOrDynamicType(
      ComplexObject(
        "foo",
        Int.MAX_VALUE,
        Double.MAX_VALUE
      )
    )
    assertEquals(23, result)
  }

  @Test
  fun `sending a map type to an overloaded method that includes Map should always choose the map`() {
    val result = myService.functionWithComplexOrDynamicType(
      mapOf(
        "name" to "foo",
        "amount" to Int.MAX_VALUE
      )
    )
    assertEquals(23, result)
  }

  private fun getFreePort(): Int {
    return (ServerSocket(0)).use {
      it.localPort
    }
  }
}