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
package io.bluebank.braid.corda.services

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.DataFeed
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.utilities.NetworkHostAndPort

/**
 * interface for access to network map services
 */
interface NetworkMapServiceAdapter {

  fun networkMapSnapshot(): List<NodeInfo>
  fun wellKnownPartyFromX500Name(x500Name: CordaX500Name): Party?
  fun nodeInfoFromParty(party: AbstractParty): NodeInfo?
  fun notaryIdentities(): List<Party>
  fun nodeInfo(): NodeInfo
  fun notaryPartyFromX500Name(x500Name: CordaX500Name): Party?
  fun track(): DataFeed<List<NodeInfo>, NetworkMapCache.MapChange>
  fun getNodeByAddress(hostAndPort: NetworkHostAndPort): NodeInfo?
  fun getNodeByAddress(hostAndPort: String): NodeInfo? {
    return getNodeByAddress(NetworkHostAndPort.parse(hostAndPort))
  }

  fun getNodeByLegalName(name: CordaX500Name): NodeInfo?
}