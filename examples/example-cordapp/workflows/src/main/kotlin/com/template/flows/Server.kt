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

import io.bluebank.braid.corda.BraidConfig
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.finance.flows.CashIssueFlow

@CordaService
class Server(private val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {

  init {
    BraidConfig.fromResource(configFileName)?.bootstrap()
  }

  private fun BraidConfig.bootstrap() {
    this.withFlow(EchoFlow::class)
      .withFlow("issueCash", CashIssueFlow::class)
      .withService("myService", MyService(serviceHub))
      .withAuthConstructor { MySimpleAuthProvider() }
      .bootstrapBraid(serviceHub)
  }

  /**
   * config file name based on the node legal identity
   */
  private val configFileName: String
    get() {
      val name = serviceHub.myInfo.legalIdentities.first().name.organisation
      return "braid-$name.json"
    }
}
