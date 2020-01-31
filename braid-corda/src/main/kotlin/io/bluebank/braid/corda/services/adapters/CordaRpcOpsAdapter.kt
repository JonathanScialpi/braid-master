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
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.utilities.NetworkHostAndPort

fun CordaRPCOps.toCordaServicesAdapter(): CordaServicesAdapter {
  return CordaRpcOpsAdapter(this)
}

class CordaRpcOpsAdapter(private val cordaRpcOps: CordaRPCOps) : CordaServicesAdapter {
  override fun getNodeByLegalName(name: CordaX500Name): NodeInfo? {
    return cordaRpcOps.networkMapSnapshot().firstOrNull { nodeInfo ->
      nodeInfo.legalIdentities.map { identity -> identity.name }.contains(name)
    }
  }

  override fun getNodeByAddress(hostAndPort: NetworkHostAndPort): NodeInfo? {
    return cordaRpcOps.networkMapSnapshot().firstOrNull {
      it.addresses.contains(hostAndPort)
    }
  }

  override fun track(): DataFeed<List<NodeInfo>, NetworkMapCache.MapChange> {
    return cordaRpcOps.networkMapFeed()
  }

  override fun notaryPartyFromX500Name(x500Name: CordaX500Name): Party? {
    return cordaRpcOps.notaryPartyFromX500Name(x500Name)
  }

  override fun nodeInfo(): NodeInfo {
    return cordaRpcOps.nodeInfo()
  }

  override fun notaryIdentities(): List<Party> {
    return cordaRpcOps.notaryIdentities()
  }

  override fun networkMapSnapshot(): List<NodeInfo> {
    return cordaRpcOps.networkMapSnapshot()
  }

  override fun wellKnownPartyFromX500Name(x500Name: CordaX500Name): Party? {
    return cordaRpcOps.wellKnownPartyFromX500Name(x500Name)
  }

  override fun nodeInfoFromParty(party: AbstractParty): NodeInfo? {
    return cordaRpcOps.nodeInfoFromParty(party)
  }

  override fun <T> startFlowDynamic(
    logicType: Class<out FlowLogic<T>>,
    vararg args: Any?
  ): FlowHandle<T> {
    return cordaRpcOps.startFlowDynamic(logicType, *args)
  }

  override fun <T> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowProgressHandle<T> {
    return cordaRpcOps.startTrackedFlowDynamic(logicType, *args)
  }
}