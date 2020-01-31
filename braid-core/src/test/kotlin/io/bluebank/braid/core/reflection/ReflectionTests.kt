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
package io.bluebank.braid.core.reflection

import io.bluebank.braid.core.annotation.ServiceDescription
import io.vertx.core.Future
import org.junit.Test
import rx.Observable
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ServiceDescription(name = "named-service", description = "named-service description")
interface NamedService {

  fun getSync(): String
  fun getAsync(): Future<String>
  fun getStream(): Observable<String>
  fun getCollection(): List<String>
}

interface UnamedService

class ReflectionTests {
  @Test
  fun `that service name is correctly extracted`() {
    assertEquals("named-service", NamedService::class.java.serviceName())
    assertEquals("unamedservice", UnamedService::class.java.serviceName())
  }

  @Test
  fun `that types can be correctly inferred`() {
    assertEquals(
      String::class.java,
      NamedService::getAsync.javaMethod!!.underlyingGenericType()
    )
    assertEquals(
      String::class.java,
      NamedService::getSync.javaMethod!!.underlyingGenericType()
    )
    assertEquals(
      String::class.java,
      NamedService::getStream.javaMethod!!.underlyingGenericType()
    )
    assertEquals(
      NamedService::getCollection.javaMethod!!.genericReturnType,
      NamedService::getCollection.javaMethod!!.underlyingGenericType()
    )
    assertTrue { NamedService::getStream.javaMethod!!.genericReturnType.isStreaming() }
    assertEquals(
      Observable::class.java,
      NamedService::getStream.javaMethod!!.genericReturnType.actualType()
    )
  }
}