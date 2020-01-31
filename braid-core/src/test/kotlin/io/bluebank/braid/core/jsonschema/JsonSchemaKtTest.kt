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
package io.bluebank.braid.core.jsonschema

import io.bluebank.braid.core.annotation.MethodDescription
import io.bluebank.braid.core.service.MethodDescriptor
import org.junit.Test
import rx.Observable
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertEquals

class JsonSchemaKtTest {

  @Test
  fun `that we can get the correct descriptor for a method with description`() {
    val method = TestService::echo.javaMethod!!
    val descriptor = method.toDescriptor()
    val match = MethodDescriptor(
      name = "echo",
      description = "echo",
      parameters = mapOf("message" to "string"),
      returnType = "string"
    )
    assertEquals(match, descriptor)
  }

  @Test
  fun `that we can get the correct descriptor for a method without description`() {
    val method = TestService::echoNoDescription.javaMethod!!
    val descriptor = method.toDescriptor()
    val match = MethodDescriptor(
      name = "echoNoDescription",
      description = "",
      parameters = mapOf("message" to "string"),
      returnType = "string"
    )
    assertEquals(match, descriptor)
  }

  @Test
  fun `that we can get the correct descriptor for a method returning observable`() {
    val method = TestService::numbers.javaMethod!!
    val descriptor = method.toDescriptor()
    val match = MethodDescriptor(
      name = "numbers",
      description = "",
      parameters = emptyMap(),
      returnType = "stream-of integer"
    )
    assertEquals(match, descriptor)
  }

  @Test
  fun `that we can get the correct descriptor for a class constructor`() {
    val constructor = EchoFlow::class.java.constructors.first()
    val descriptor = constructor.toDescriptor()
    val match = MethodDescriptor(
      name = EchoFlow::class.qualifiedName!!,
      description = EchoFlow::class.simpleName!!,
      parameters = mapOf("message" to "string"),
      returnType = "{message:string}"
    )
    assertEquals(match, descriptor)
  }

  @Test
  fun `simple javascript type for various types`() {
    assertEquals("string", "foo".javaClass.toSimpleJavascriptType())
    assertEquals("array", arrayOf("one", "two").javaClass.toSimpleJavascriptType())
    assertEquals("integer", 1.javaClass.toSimpleJavascriptType())
  }

}

class TestService(val config: String) {
  @MethodDescription(description = "echo", returnType = String::class)
  fun echo(message: String) = message

  fun echoNoDescription(message: String) = message
  fun numbers(): Observable<Int> = Observable.just(1, 2, 3)
}

class EchoFlow(val message: String) {
  fun call(): String = message
}