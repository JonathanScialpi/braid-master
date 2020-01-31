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
package io.bluebank.braid.corda.services.adapters

import io.bluebank.braid.corda.services.CordaServicesAdapter
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.utilities.NetworkHostAndPort

class RPCFactoryCordaServicesAdapter(
  private val delegate: CordaServicesAdapter
) : CordaServicesAdapter {

  override fun <T> startFlowDynamic(
    logicType: Class<out FlowLogic<T>>,
    vararg args: Any?
  ): FlowHandle<T> {
    return delegate.startFlowDynamic(logicType, *args)
  }

  override fun <T> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowProgressHandle<T> {
    return delegate.startTrackedFlowDynamic(logicType, *args)
  }

  override fun networkMapSnapshot(): List<NodeInfo> {
    return delegate.networkMapSnapshot()
  }

  override fun wellKnownPartyFromX500Name(x500Name: CordaX500Name): Party? {
    return delegate.wellKnownPartyFromX500Name(x500Name)
  }

  override fun nodeInfoFromParty(party: AbstractParty): NodeInfo? {
    return delegate.nodeInfoFromParty(party)
  }

  override fun notaryIdentities(): List<Party> {
    return delegate.notaryIdentities()
  }

  override fun nodeInfo(): NodeInfo {
    return delegate.nodeInfo()
  }

  override fun notaryPartyFromX500Name(x500Name: CordaX500Name): Party? {
    return delegate.notaryPartyFromX500Name(x500Name)
  }

  override fun track(): DataFeed<List<NodeInfo>, NetworkMapCache.MapChange> {
    return delegate.track()
  }

  override fun getNodeByAddress(hostAndPort: NetworkHostAndPort): NodeInfo? {
    return delegate.getNodeByAddress(hostAndPort)
  }

  override fun getNodeByLegalName(name: CordaX500Name): NodeInfo? {
    return delegate.getNodeByLegalName(name)
  }
}
