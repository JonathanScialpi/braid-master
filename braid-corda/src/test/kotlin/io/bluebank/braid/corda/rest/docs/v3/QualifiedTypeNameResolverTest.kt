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

import com.fasterxml.jackson.databind.type.TypeFactory
import net.corda.core.contracts.Amount
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.lang.reflect.Type
import java.util.*
import kotlin.reflect.jvm.javaType

class QualifiedTypeNameResolverTest {

  private val resolver = QualifiedTypeNameResolver();

  @Test
  fun `should use simple name for java types`() {
    assertThat(resolver.nameForType(javatype(Currency::class.java)), equalTo("Currency"))
  }

  @Test
  fun `should be dot separated type for user types`() {
    assertThat(
      resolver.nameForType(javatype(QualifiedTypeNameResolverTest::class.java)),
      equalTo("io.bluebank.braid.corda.rest.docs.v3.QualifiedTypeNameResolverTest")
    )
  }

  @Test
  fun `should be underscore separated type for Inner user types`() {
    assertThat(
      resolver.nameForType(javatype(Inner::class.java)),
      equalTo("io.bluebank.braid.corda.rest.docs.v3.QualifiedTypeNameResolverTest_Inner")
    )
  }

  @Test
  fun `should be underscore separated type for Generic user types`() {
    assertThat(resolver.nameForType(javatype(Amount::class.java)), equalTo("net.corda.core.contracts.Amount"))
  }

  @Test
  fun `should resolve generic type parameters with underscore for package name`() {
    val firstParam = this::f1.parameters.first()
    val javaType = javatype(firstParam.type.javaType)
    val resolved = resolver.nameForType(javaType)
    assertThat(resolved, equalTo("io.bluebank.braid.corda.rest.docs.v3.Box_io_bluebank_braid_corda_rest_docs_v3_Apple"))
  }

  private fun javatype(type: Type) = TypeFactory.defaultInstance().constructType(type)

  private fun f1(@Suppress("UNUSED_PARAMETER") appleBox: Box<Apple>) {
  }
  private class Inner

}

private interface Fruit {
  val name: String
}

private class Banana : Fruit {
  override val name: String get() = "Banana"
}

private class Apple : Fruit {
  override val name: String get() = "Apple"
}

private class Box<T : Fruit>
