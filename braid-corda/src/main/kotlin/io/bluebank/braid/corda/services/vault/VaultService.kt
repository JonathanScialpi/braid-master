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

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.vertx.ext.auth.User
import net.corda.core.contracts.ContractState
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.Vault
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Context

class VaultService(
  private val getCordaRPCOps: (User?) -> CordaRPCOps
) {

  @Operation(description = "Queries the vault")
  fun vaultQueryBy(
    @Parameter(
      description = "Vault query parameters"
    ) vault: VaultQuery,
    @Context user: User?
  ): Vault.Page<ContractState> {
    val vaultQueryBy = getCordaRPCOps(user)
      .vaultQueryBy(vault.criteria, vault.paging, vault.sorting, vault.contractStateType)
    return vaultQueryBy
  }

  @Operation(description = "Queries the vault for contract states of the supplied type")
  fun vaultQuery(
    @QueryParam(value = "contract-state-type")
    @Parameter(
      description = "The NAME of the Vault query by contract state type class e.g. \"net.corda.finance.contracts.asset.Obligation.State\""
    ) type: String?,
    @Context user: User?
  ): Vault.Page<ContractState> {
    return try {
      @Suppress("UNCHECKED_CAST") val forName =
        if (type != null && type != "")
          Class.forName(
            type,
            false,
            Thread.currentThread().contextClassLoader
          ) as Class<ContractState>
        else ContractState::class.java

      getCordaRPCOps(user).vaultQuery(forName)
    } catch (e: Exception) {
      throw RuntimeException("Unable to query contract state:" + type, e)
    }
  }
}