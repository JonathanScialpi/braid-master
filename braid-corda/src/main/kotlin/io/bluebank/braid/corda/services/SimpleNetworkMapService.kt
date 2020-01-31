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
@file:JvmName("SimpleNetworkMapServiceImplKt")

package io.bluebank.braid.corda.services

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.vertx.ext.auth.User
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.utilities.NetworkHostAndPort
import rx.Observable
import rx.Subscription
import java.util.stream.Collectors
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Context

data class SimpleNodeInfo(
  val addresses: List<NetworkHostAndPort>,
  val legalIdentities: List<Party>
) {

  // we map to work around the serialisation of
  constructor(nodeInfo: NodeInfo) : this(nodeInfo.addresses, nodeInfo.legalIdentities)
}

fun NodeInfo.toSimpleNodeInfo(): SimpleNodeInfo {
  return SimpleNodeInfo(this.addresses, this.legalIdentities)
}

// This is exposed via RPC only, not REST: a different set of methods is exposed for REST
interface SimpleNetworkMapService {

  fun allNodes(): List<SimpleNodeInfo>
  fun state(): Observable<Any>
  fun notaryIdentities(): List<Party>
  fun getNotary(cordaX500Name: CordaX500Name): Party?
  fun getNodeByAddress(hostAndPort: String): SimpleNodeInfo?
  fun getNodeByLegalName(name: CordaX500Name): SimpleNodeInfo?
}

class SimpleNetworkMapServiceImpl(
  private val networkMapServiceAdapter: NetworkMapServiceAdapter
) : SimpleNetworkMapService {

  enum class MapChangeType {
    ADDED,
    REMOVED,
    MODIFIED
  }

  data class MapChange(
    val type: MapChangeType,
    val node: SimpleNodeInfo,
    val previousNode: SimpleNodeInfo? = null
  ) {

    constructor(change: NetworkMapCache.MapChange) : this(
      when (change) {
        is NetworkMapCache.MapChange.Added -> MapChangeType.ADDED
        is NetworkMapCache.MapChange.Removed -> MapChangeType.REMOVED
        is NetworkMapCache.MapChange.Modified -> MapChangeType.MODIFIED
        else -> throw RuntimeException("unknown map change type ${change.javaClass}")
      },
      change.node.toSimpleNodeInfo(),
      when (change) {
        is NetworkMapCache.MapChange.Modified -> change.previousNode.toSimpleNodeInfo()
        else -> null
      }
    )
  }

  override fun allNodes(): List<SimpleNodeInfo> {
    return networkMapServiceAdapter.networkMapSnapshot().map {
      it.toSimpleNodeInfo()
    }
  }

  override fun state(): Observable<Any> {
    @Suppress("DEPRECATION")
    return Observable.create { subscriber ->
      val dataFeed = networkMapServiceAdapter.track()
      val snapshot = dataFeed.snapshot.map { SimpleNodeInfo(it) }
      subscriber.onNext(snapshot)
      var subscription: Subscription? = null

      subscription = dataFeed.updates.subscribe { change ->
        if (subscriber.isUnsubscribed) {
          subscription?.unsubscribe()
          subscription = null
        } else {
          subscriber.onNext(change.asSimple())
        }
      }
    }
  }

  override fun notaryIdentities(): List<Party> {
    return networkMapServiceAdapter.notaryIdentities()
  }

  override fun getNotary(cordaX500Name: CordaX500Name): Party? {
    return networkMapServiceAdapter.notaryPartyFromX500Name(cordaX500Name)
  }

  override fun getNodeByAddress(hostAndPort: String): SimpleNodeInfo? {
    return networkMapServiceAdapter.getNodeByAddress(hostAndPort)?.toSimpleNodeInfo()
  }

  override fun getNodeByLegalName(name: CordaX500Name): SimpleNodeInfo? {
    return networkMapServiceAdapter.getNodeByLegalName(name)?.toSimpleNodeInfo()
  }
}

private fun NetworkMapCache.MapChange.asSimple(): SimpleNetworkMapServiceImpl.MapChange {
  return SimpleNetworkMapServiceImpl.MapChange(this)
}

fun <T> AppServiceHub.transaction(fn: () -> T): T {
  return fn()
}

/**
 * The SimpleNetworkMapService interface and its *Impl class are the API exposed to clients via the RPC protocol.
 * Conversely this here is the API which Braid exposes to its clients via its REST protocol.
 */
class RestNetworkMapService(
  private val getNetworkMapServiceAdapter: (User?) -> NetworkMapServiceAdapter
) {

  @Operation(description = "Retrieves all nodes if neither query parameter is supplied. Otherwise returns a list of one node matching the supplied query parameter.")
  fun myNodeInfo(
    @Context user: User?
  ): SimpleNodeInfo {
    val networkMapServiceAdapter = getNetworkMapServiceAdapter(user)
    return networkMapServiceAdapter.nodeInfo().toSimpleNodeInfo()
  }

  @Operation(description = "Retrieves all nodes if neither query parameter is supplied. Otherwise returns a list of one node matching the supplied query parameter.")
  fun nodes(
    @Parameter(
      description = "[host]:[port] for the Corda P2P of the node",
      example = "localhost:10000"
    ) @QueryParam(value = "host-and-port") hostAndPort: String?,
    @Parameter(
      description = "the X500 name for the node",
      example = "O=PartyB, L=New York, C=US"
    ) @QueryParam(value = "x500-name") x500Name: String?,
    @Context user: User?
  ): List<SimpleNodeInfo> {
    val networkMapServiceAdapter = getNetworkMapServiceAdapter(user)
    return when {
      hostAndPort?.isNotEmpty() ?: false -> {
        val address = NetworkHostAndPort.parse(hostAndPort!!)
        networkMapServiceAdapter.networkMapSnapshot().stream()
          .filter { node -> node.addresses.contains(address) }
          .map { node -> node.toSimpleNodeInfo() }
          .collect(Collectors.toList())
      }
      x500Name?.isNotEmpty() ?: false -> {
        val x500Name1 = CordaX500Name.parse(x500Name!!)
        val party = networkMapServiceAdapter.wellKnownPartyFromX500Name(x500Name1)
        listOfNotNull(party?.let {
          networkMapServiceAdapter.nodeInfoFromParty(party)?.toSimpleNodeInfo()
        })
      }
      else -> networkMapServiceAdapter.networkMapSnapshot().stream().map { node -> node.toSimpleNodeInfo() }.collect(
        Collectors.toList()
      )
    }
  }

  // example http://localhost:8080/api/rest/network/notaries?x500-name=O%3DNotary%20Service,%20L%3DZurich,%20C%3DCH
  fun notaries(
    @Parameter(
      description = "the X500 name for the node",
      example = "O=PartyB, L=New York, C=US"
    ) @QueryParam(value = "x500-name") x500Name: String?,
    @Context user: User?
  ): List<Party> {
    val networkMapServiceAdapter = getNetworkMapServiceAdapter(user)
    return when {
      x500Name?.isNotEmpty() ?: false -> listOfNotNull(
        networkMapServiceAdapter.notaryPartyFromX500Name(
          CordaX500Name.parse(x500Name!!)
        )
      )
      else -> networkMapServiceAdapter.notaryIdentities()
    }
  }
}