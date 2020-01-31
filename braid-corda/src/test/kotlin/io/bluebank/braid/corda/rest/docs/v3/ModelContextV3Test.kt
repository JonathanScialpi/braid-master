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

import io.bluebank.braid.corda.rest.docs.BraidSwaggerError
import io.bluebank.braid.corda.serialisation.serializers.BraidCordaJacksonInit
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.transactions.TraversableTransaction
import net.corda.core.transactions.WireTransaction
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.junit.Assert.assertThat
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelContextV3Test {
  companion object {
    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      BraidCordaJacksonInit.init()
    }
  }

  @Test
  fun `should exclude availableComponentGroups from TraversableTransaction`() {

    val modelContext = ModelContextV3()
    modelContext.addType(TraversableTransaction::class.java)

    val wire = modelContext.models.get(TraversableTransaction::class.java.swaggerTypeName())
    assertThat(wire, notNullValue())
    assertThat(wire?.properties?.get("availableComponentGroups"), nullValue())

  }

  @Test
  fun `should exclude availableComponentGroups from WireTransaction`() {

    val modelContext = ModelContextV3()
    modelContext.addType(WireTransaction::class.java)

    val wire = modelContext.models.get(WireTransaction::class.java.swaggerTypeName())
    assertThat(wire, notNullValue())
    assertThat(wire?.properties?.get("availableComponentGroups"), nullValue())

  }

  @Test
  fun `should exclude length from TimeWindow`() {

    val modelContext = ModelContextV3()
    modelContext.addType(TimeWindow::class.java)

    val window = modelContext.models.get(TimeWindow::class.java.name)
    assertThat(window, notNullValue())
    assertThat(window?.properties?.get("length"), nullValue())

  }

  @Test
  fun `that InvalidAttachmentException excludes cause`() {
    val modelContext = ModelContextV3()
    modelContext.addType(TransactionVerificationException.InvalidAttachmentException::class.java)

    assertFalse(modelContext.models.containsKey(TransactionVerificationException.InvalidAttachmentException::class.java.swaggerTypeName()))
    assertTrue(modelContext.models.containsKey("InvocationError"))
    val exception = modelContext.models["InvocationError"]
    assertThat(exception, notNullValue())
    assertThat(exception?.properties?.get("cause"), nullValue())
  }

  @Test
  fun `that PackageOwnershipException excludes cause`() {
    val modelContext = ModelContextV3()
    modelContext.addType(TransactionVerificationException.PackageOwnershipException::class.java)

    val exception = modelContext.models["InvocationError"]
    assertThat(exception, notNullValue())
    assertThat(exception?.properties?.get("cause"), nullValue())
  }

  @Test
  @Ignore
  fun `that PartyAndCertificate is modelled as per serializer`() {
    val modelContext = ModelContextV3()
    modelContext.addType(PartyAndCertificate::class.java)

    val exceptn = modelContext.models.get(PartyAndCertificate::class.java.swaggerTypeName())
    assertThat(exceptn, notNullValue())

  }

  @Test
  fun `that we model errors correctly`() {
    val modelContext = ModelContextV3()
    modelContext.addType(BraidSwaggerError::class.java)
    assertTrue(
      modelContext.models.containsKey("InvocationError"),
      "that the name of the error type is Error without the package name"
    )
  }

}