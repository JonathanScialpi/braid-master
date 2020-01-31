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
package com.template.flows

import io.bluebank.braid.corda.services.transaction
import io.bluebank.braid.core.annotation.MethodDescription
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.Vault
import net.corda.finance.contracts.asset.Cash
import rx.Observable

class MyService(private val serviceHub: AppServiceHub) {

  @MethodDescription(
    description = "listens for cash state updates in the vault",
    returnType = Vault.Update::class
  )
  fun listenForCashUpdates(): Observable<Vault.Update<Cash.State>> {
    return serviceHub.transaction {
      serviceHub.vaultService.trackBy(Cash.State::class.java).updates
    }
  }
}