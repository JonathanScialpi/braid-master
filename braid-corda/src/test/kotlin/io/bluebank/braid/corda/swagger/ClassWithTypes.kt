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
package io.bluebank.braid.corda.swagger

import io.bluebank.braid.corda.swagger.v3.CustomModelConvertersV3Test
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import java.util.*

data class ClassWithTypes(
    val currency: Currency,
    val amountCurrency: Amount<Currency>
    , val amountString: Amount<String>
    , val amount: Amount<Any>
    , val party: Party
    , val bytes: OpaqueBytes
    , val hash: SecureHash
    , val issuedString: Issued<String>
    , val issuedCurrency: Issued<Currency>
    , val signed: SignedTransaction
    , val wire: WireTransaction
    , val clazz: Class<*>
)