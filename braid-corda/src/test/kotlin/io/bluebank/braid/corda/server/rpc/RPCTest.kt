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
package io.bluebank.braid.corda.server.rpc

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import io.bluebank.braid.corda.serialisation.serializers.BraidCordaJacksonInit
import io.bluebank.braid.corda.services.vault.VaultQuery
import io.bluebank.braid.core.async.toFuture
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.Future
import io.vertx.core.json.Json
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.ContractState
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.Builder.greaterThanOrEqual
import net.corda.core.node.services.vault.ColumnPredicate
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.toObservable
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.AMOUNT
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.schemas.CashSchemaV1
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class RPCTest

private val log = loggerFor<RPCTest>()

fun main(args: Array<String>) {

  try {
    if (args.size != 3) {
      throw IllegalArgumentException("Usage: RPCTest <node address> <username> <password>")
    }
    val nodeAddress = NetworkHostAndPort.parse(args[0])
    val username = args[1]
    val password = args[2]

    val client = CordaRPCClient(nodeAddress)
    val connection = client.start(username, password)
    val ops = connection.proxy

BraidCordaJacksonInit.init()

    val it = SimpleModule()
      .addSerializer(rx.Observable::class.java, ToStringSerializer())

    Json.mapper.registerModule(it)
    Json.prettyMapper.registerModule(it)

    //issueCash(ops)

    //info(ops)

    //println(ops.vaultQuery(Cash.State::class.java))
    //printJson(vaultQueryBy1(ops))

    printJson(vaultQueryBy2(ops))

    connection.notifyServerAndClose()
  } catch (e: Exception) {
    log.error("Unable to run rpc", e)
  }
}

fun printJson(output: Any) {
  println(Json.encodePrettily(output))
}

//private fun vaultQueryBy1(ops: CordaRPCOps): Vault.Page<ContractState> {
//  val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
//  val currencyIndex = Cash.State::amount.greaterThanOrEqual(Amount<Issued<Currency>>(5, Issued(PartyAndReference(notary(ops),OpaqueBytes("123".toByteArray())) ,Currency.getInstance("GBP")))))
//  val quantityIndex = SampleCashSchemaV3.PersistentCashState::pennies.greaterThanOrEqual(0L)
//
//  val customCriteria2 = QueryCriteria.VaultCustomQueryCriteria(quantityIndex)
//  val customCriteria1 = QueryCriteria.VaultQueryCriteria(currencyIndex)
//
//  val criteria = generalCriteria
//      .and(customCriteria1)
//    //  .and(customCriteria2)
//  val results = ops.vaultQueryBy(criteria, PageSpecification(), Sort(emptyList()), Cash.State::class.java)
//  return results
//}

private fun vaultQueryBy2(ops: CordaRPCOps): Vault.Page<ContractState> {
  val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
  val currencyIndex = CashSchemaV1.PersistentCashState::currency.equal("GBP")
  val quantityIndex = CashSchemaV1.PersistentCashState::pennies.greaterThanOrEqual(0L)

  val customCriteria2 = QueryCriteria.VaultCustomQueryCriteria(quantityIndex)
  val customCriteria1 = QueryCriteria.VaultCustomQueryCriteria(currencyIndex)

  val criteria = generalCriteria
      .and(customCriteria1)
      .and(customCriteria2)

  VaultQuery(criteria, PageSpecification(), Sort(emptyList()), Cash.State::class.java)
  val results = ops.vaultQueryBy(criteria, PageSpecification(), Sort(emptyList()), Cash.State::class.java)
  return results
}

private fun vaultQueryBy1(ops: CordaRPCOps): Vault.Page<ContractState> {
  val start = Instant.now().minus(15, ChronoUnit.DAYS)
  val end = start.plus(30, ChronoUnit.DAYS)
  val recordedBetweenExpression = QueryCriteria.TimeCondition(
    QueryCriteria.TimeInstantType.RECORDED,
    ColumnPredicate.Between(start, end)
  )
  val criteria =
    QueryCriteria.VaultQueryCriteria(timeCondition = recordedBetweenExpression)

//  val criteria = QueryCriteria.LinearStateQueryCriteria(participants = asList(notary(ops) as AbstractParty))
//  val sorting = Sort(
//    listOf(
//      Sort.SortColumn(
//        SortAttribute.Standard(Sort.VaultStateAttribute.CONTRACT_STATE_TYPE),
//        Sort.Direction.ASC
//      )
//    )
//  )

  val q = VaultQuery(criteria = criteria)
  println(Json.encodePrettily(q))

  val results = ops.vaultQueryBy(q.criteria, q.paging, q.sorting, q.contractStateType)
  return results
}

@Suppress("unused")
private fun issueCash(ops: CordaRPCOps): Future<AbstractCashFlow.Result> {
//  val party = Party(
//    CordaX500Name.parse("O=Notary Service, L=Zurich, C=CH"),
//    parsePublicKeyBase58("GfHq2tTVk9z4eXgyVjEnMc2NbZTfJ6Y3YJDYNRvPn2U7jiS3suzGY1yqLhgE")
//  )
  val progressHandler = ops.startFlowDynamic(
    CashIssueFlow::class.java,
    AMOUNT(10.00, Currency.getInstance("GBP")),
    OpaqueBytes("123".toByteArray()),
    notary(ops)
  )
  return progressHandler.returnValue.toObservable().toFuture()
}

private fun notary(ops: CordaRPCOps) =
    ops.notaryPartyFromX500Name(CordaX500Name.parse("O=Notary Service, L=Zurich, C=CH"))!!

private fun info(ops: CordaRPCOps) {
  log.info("currentNodeTime" + Json.encodePrettily(ops.currentNodeTime()))
  log.info("nodeInfo" + Json.encodePrettily(ops.nodeInfo()))
  log.info("nodeInfo/addresses" + Json.encodePrettily(ops.nodeInfo().addresses))
  log.info("nodeInfo/legalIdentities" + Json.encodePrettily(ops.nodeInfo().legalIdentities))
  //  log.info(cordaRPCOperations.nodeInfoFromParty(Party(CordaX500Name.parse(""), PublicKey())).toString())
  log.info("notaryIdentities:" + Json.encodePrettily(ops.notaryIdentities()))
  log.info("networkMapFeed:" + Json.encodePrettily(ops.networkMapFeed()))
  log.info("registeredFlows:" + Json.encodePrettily(ops.registeredFlows()))
}

