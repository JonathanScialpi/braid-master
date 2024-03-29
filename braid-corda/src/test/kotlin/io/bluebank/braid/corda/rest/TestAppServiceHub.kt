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
package io.bluebank.braid.corda.rest

import net.corda.core.contracts.*
import net.corda.core.cordapp.CordappProvider
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.node.AppServiceHub
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.*
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.TestIdentity
import java.sql.Connection
import java.time.Clock
import java.util.function.Consumer
import javax.persistence.EntityManager

/**
 * A stub class to simulate the service hub - we need this for Braid Corda
 * Technically braid-corda's core could be moved to core
 */
class TestAppServiceHub : AppServiceHub {

  override val networkParametersService: NetworkParametersService
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

  override fun loadContractAttachment(stateRef: StateRef): Attachment {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override val attachments: AttachmentStorage
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val clock: Clock
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val contractUpgradeService: ContractUpgradeService
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val cordappProvider: CordappProvider
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val identityService: IdentityService
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val keyManagementService: KeyManagementService
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val myInfo: NodeInfo
    get() = NodeInfo(
      listOf(NetworkHostAndPort("localhost", 10001)),
      listOf(TestIdentity(DUMMY_BANK_A_NAME, 40).identity),
      3,
      1
    )
  override val networkMapCache: NetworkMapCache
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val networkParameters: NetworkParameters
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val transactionVerifierService: TransactionVerifierService
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val validatedTransactions: TransactionStorage
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val vaultService: VaultService
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

  override fun <T : SerializeAsToken> cordaService(type: Class<T>): T {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun jdbcSession(): Connection {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun loadState(stateRef: StateRef): TransactionState<*> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun recordTransactions(
    statesToRecord: StatesToRecord,
    txs: Iterable<SignedTransaction>
  ) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun registerUnloadHandler(runOnStop: () -> Unit) {
  }

  override fun <T> startFlow(flow: FlowLogic<T>): FlowHandle<T> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun <T> startTrackedFlow(flow: FlowLogic<T>): FlowProgressHandle<T> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun withEntityManager(block: Consumer<EntityManager>) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun <T> withEntityManager(block: EntityManager.() -> T): T {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}