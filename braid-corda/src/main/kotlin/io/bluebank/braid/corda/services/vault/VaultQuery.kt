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
package io.bluebank.braid.corda.services.vault

import com.fasterxml.jackson.annotation.JsonInclude
import net.corda.core.contracts.ContractState
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
data class VaultQuery(
  val criteria: QueryCriteria = QueryCriteria.VaultQueryCriteria(),
  val paging: PageSpecification = PageSpecification(),
  val sorting: Sort = Sort(emptyList()),
  val contractStateType: Class<out ContractState> = ContractState::class.java
) {

  fun withQueryCriteria(criteria: QueryCriteria): VaultQuery {
    return copy(criteria = criteria)
  }

  fun withPageSpecification(paging: PageSpecification): VaultQuery {
    return copy(paging = paging)
  }

  fun withSorting(sorting: Sort): VaultQuery {
    return copy(sorting = sorting)
  }

  fun withContractStateType(clazz: Class<ContractState>): VaultQuery {
    return copy(contractStateType = clazz)
  }

}