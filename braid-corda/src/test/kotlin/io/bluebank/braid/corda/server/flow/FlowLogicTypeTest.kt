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
package io.bluebank.braid.corda.server.flow

import io.bluebank.braid.corda.server.BraidTestFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashIssueFlow
import org.junit.Test
import kotlin.reflect.jvm.jvmErasure
import kotlin.test.assertEquals

class FlowLogicTypeTest {

@Test
  fun shouldGetReturnType() {
    val returnType = BraidTestFlow::class.flowLogicType()
    assertEquals(returnType.jvmErasure, SignedTransaction::class)
  }

  @Test
  fun shouldGetReturnTypeForCashIssueFlow() {
    val returnType = CashIssueFlow::class.flowLogicType()
    assertEquals(returnType.jvmErasure, AbstractCashFlow.Result::class)
  }

  @Test
  fun shouldGetReturnTypeForCashIssueAndPaymentFlow() {
    val returnType = CashIssueAndPaymentFlow::class.flowLogicType()
    assertEquals(returnType.jvmErasure, AbstractCashFlow.Result::class)
  }
}