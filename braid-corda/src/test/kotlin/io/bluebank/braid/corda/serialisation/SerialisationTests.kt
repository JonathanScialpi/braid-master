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
package io.bluebank.braid.corda.serialisation

import io.bluebank.braid.corda.BraidCordaJacksonSwaggerInit
import io.bluebank.braid.corda.services.vault.VaultQuery
import io.vertx.core.json.Json
import net.corda.core.contracts.*
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.Party
import net.corda.core.internal.lazyMapped
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.Builder.greaterThanOrEqual
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.GBP
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.test.SampleCashSchemaV1
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.withTestSerializationEnvIfNotSet
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat
import org.junit.BeforeClass
import org.junit.Test
import sun.security.provider.X509Factory
import java.io.ByteArrayInputStream
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.jvm.javaType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerialisationTests {
  companion object {
    val DUMMY_BANK_A = TestIdentity(DUMMY_BANK_A_NAME, 40).party
    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      BraidCordaJacksonSwaggerInit.init()
    }
  }

//  @Ignore
  @Test     // fails because we cant tell if this is a String or a Currency
  fun `that Amount of String token can be serialised and deserialised`() {
    val expected = Amount(100, "GBP")
    val encoded = Json.encode(expected)
    val actual = Json.decodeValue(encoded, Amount::class.java)
    assertEquals(expected, actual)
  }

  @Test
  fun `that Amount of Currency token can be serialised and deserialised`() {
    val expected = Amount(100, GBP)
    val encoded = Json.encode(expected)
    val actual = Json.decodeValue(encoded, Amount::class.java)
    assertEquals(expected, actual)
  }

  @Test
  fun `that Amount of Issued Currency can be serialised and deserialised`() {
    val expected =
      Amount(100, Issued(PartyAndReference(DUMMY_BANK_A, OpaqueBytes.of(0x01)), GBP))
    val encoded = Json.encode(expected)
    val actual = Json.decodeValue(encoded, Amount::class.java)
    assertEquals(expected, actual)
  }

  @Test
  fun `that Date should serialized using ISO8601`() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    val expected = sdf.parse("2019-03-30 12:34:56.567")
    val encoded = Json.encode(expected)

    assertEquals("\"2019-03-30T12:34:56.567+0000\"", encoded)
    val decoded = Json.decodeValue(encoded, Date::class.java)
    assertEquals(decoded, expected)
  }

  @Test
  fun `that X509 Should serialize as bytes`() {
    val base64 = this::class.java.getResource("/serialization/certificate/x509.pem")
        .readText()
      .replace(X509Factory.BEGIN_CERT, "")
      .replace(X509Factory.END_CERT, "")
      .replace("\n", "")
      .replace("\r", "")

    val certificate = CertificateFactory.getInstance("X.509")
        .generateCertificate(ByteArrayInputStream(Base64.getDecoder().decode(base64)))

    val encoded = Json.encode(certificate)
    val decoded = Json.decodeValue(encoded, X509Certificate::class.java)

    assertThat(encoded, startsWith("\"MIIIRzCCBi"))
    assertEquals(decoded, certificate)
  }

  @Test
  fun `given a non empty set we can deserialise it`() {
    val json = """
      [
        "item1", 
        "item2"
      ]
    """.trimIndent()
    val type = ::f.parameters.first().type.javaType
    val javaType = Json.mapper.typeFactory.constructType(type)
    val result = Json.mapper.readValue<NonEmptySet<String>>(json, javaType)
    assertTrue(result is NonEmptySet)
    assertEquals(2, result.size)
    assertTrue(result.contains("item1") && result.contains("item2"))
  }

  @Test
  fun `should serialize Transaction state of Cash Contract State`() {
    val partyRef = PartyAndReference(DUMMY_BANK_A, OpaqueBytes.of(0x01))
    val state = Cash.State(partyRef,
        Amount(100, GBP),
        partyRef.party)

    val txnState = TransactionState(state, state.javaClass.name, DUMMY_BANK_A)

    val encoded = Json.encodePrettily(txnState)
    val decoded = Json.decodeValue(encoded, TransactionState::class.java)
    assertEquals(decoded, txnState)
  }

  @Test
  fun `Should serialize queryCriteria`() {

    val json = """
{
  "criteria" : {
    "@class" : ".QueryCriteria${'$'}VaultQueryCriteria",
    "status" : "UNCONSUMED",
    "contractStateTypes" : null,
    "stateRefs" : null,
    "notary" : null,
    "softLockingCondition" : null,
    "timeCondition" : {
      "type" : "RECORDED",
      "predicate" : {
        "@class" : ".ColumnPredicate${'$'}Between",
        "rightFromLiteral" : "2019-09-15T12:58:23.283Z",
        "rightToLiteral" : "2019-10-15T12:58:23.283Z"
      }
    },
    "relevancyStatus" : "ALL",
    "constraintTypes" : [ ],
    "constraints" : [ ],
    "participants" : null
  },
  "paging" : {
    "pageNumber" : -1,
    "pageSize" : 200
  },
  "sorting" : {
    "columns" : [ ]
  },
  "contractStateType" : "net.corda.core.contracts.ContractState"
}
"""
    val decodeValue = Json.decodeValue(json, VaultQuery::class.java)
    assertThat(decodeValue, notNullValue())
  }

  @Test
  fun `should serialize complex vault query`() {
    val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)

    val currencyIndex = SampleCashSchemaV1.PersistentCashState::currency.equal("USD")
    val quantityIndex = SampleCashSchemaV1.PersistentCashState::pennies.greaterThanOrEqual(1L)

    val customCriteria2 = QueryCriteria.VaultCustomQueryCriteria(quantityIndex)
    val customCriteria1 = QueryCriteria.VaultCustomQueryCriteria(currencyIndex)

    val criteria = generalCriteria.and(customCriteria1).and(customCriteria2)
    val query = VaultQuery(criteria)

    val json = Json.encodePrettily(query)
    Json.decodeValue(json, VaultQuery::class.java)
  }

  @Test
  fun `should serialize criteria`() {
    val currencyIndex = SampleCashSchemaV1.PersistentCashState::currency.equal("USD")
    val customCriteria1 = QueryCriteria.VaultCustomQueryCriteria(currencyIndex)

    val json = Json.encodePrettily(customCriteria1)
    Json.decodeValue(json, QueryCriteria.VaultCustomQueryCriteria::class.java)
  }

  @Test
  fun `should serialize SampleCashSchemaV1$PersistentCashState`() {
    val expression = CriteriaExpression.ColumnPredicateExpression(Column(SampleCashSchemaV1.PersistentCashState::currency),
        ColumnPredicate.NullExpression(NullOperator.NOT_NULL))

    val json = Json.encodePrettily(expression)
    Json.decodeValue(json, CriteriaExpression.ColumnPredicateExpression::class.java)
  }

//  @Test
//  fun `should serialize all OpaqueBytesClasses`() {
//    val res = ClassGraph()
//        .enableClassInfo()
//        .whitelistPackages("net.corda")
//        .blacklistPackages(
//            "net.corda.internal",
//            "net.corda.client",
//            "net.corda.core.internal",
//            "net.corda.nodeapi.internal",
//            "net.corda.serialization.internal",
//            "net.corda.testing",
//            "net.corda.common.configuration.parsing.internal",
//            "net.corda.finance.internal",
//            "net.corda.common.validation.internal",
//            "net.corda.client.rpc.internal",
//            "net.corda.core.cordapp",
//            "net.corda.core.messaging",
//            "net.corda.node.services.statemachine"
//        )
//        .scan()
//
//    val toList = res.allClasses.asSequence()
//        .filter {
//          OpaqueBytes::class.java.isAssignableFrom(it.loadClass())
//        }
//        .map { it.loadClass() }
//        .toList()
//
//    println(toList)
//  }

  @Test
  fun `can serialize deserialize OpaqueBytes`() {
    val msg = OpaqueBytes("This is a test message".toByteArray())

    val json = Json.encode(msg)
    val deserialized = Json.decodeValue(json, OpaqueBytes::class.java)

    assertEquals(json, "{\"bytes\":\"VGhpcyBpcyBhIHRlc3QgbWVzc2FnZQ==\"}")
    assertEquals(deserialized, msg)
  }

  @Test
  fun `can serialize deserialize ByteArray`() {
    val msg = "This is a test message"
    val bytes = msg.toByteArray()
    val json = Json.encode(bytes)
    val deserializedBytes = Json.decodeValue(json, ByteArray::class.java)
    val deserializedMsg = String(deserializedBytes)
    assertEquals(deserializedMsg, msg)
  }

  @Test
  fun `TransactionSignature serialisation`() {
    val txSig = createTransactionSignature()
    val encoded = Json.encode(txSig)
    val txSig2 = Json.decodeValue(encoded, TransactionSignature::class.java)
    assertEquals(txSig, txSig2)
  }

  @Test
  fun `SerializedBytes serialisation`() {
    withTestSerializationEnvIfNotSet {
      val txSig = Command(Cash.Commands.Issue(), listOf(DUMMY_BANK_A.owningKey)).serialize()
      val encoded = Json.encode(txSig)

      val txSig2 = Json.decodeValue(encoded, SerializedBytes::class.java)
      assertEquals(txSig, txSig2)
    }
  }

  @Test
  fun `SignedTransaction serialisation from CashFlowIssue`() {
    val json = this::class.java.getResource("/serialization/signedTransaction/signedTransaction.json").readText()

    // only works with this set or
    //  @Rule @JvmField
    //  val testSerialization = SerializationEnvironmentRule()

    withTestSerializationEnvIfNotSet {
      Json.decodeValue(json, SignedTransaction::class.java)
    }
  }

  @Test
  fun `SignedTransaction serialisation`() {
    val notary = Party(DUMMY_NOTARY_NAME, generatePublicKey())
    val serialize = { value: Any, _: Int -> value.serialize() }
    val notaryGroup = ComponentGroup(ComponentGroupEnum.NOTARY_GROUP.ordinal, listOf(notary).lazyMapped(serialize))
    val state = Cash.State(Amount.parseCurrency("$100.00").`issued by`(PartyAndReference(DUMMY_BANK_A, OpaqueBytes.of(0x01))), DUMMY_BANK_A)
    val outputState = TransactionState(state, Cash.PROGRAM_ID, notary)
    val outputs = listOf(outputState)
    val outputsGroup = ComponentGroup(ComponentGroupEnum.OUTPUTS_GROUP.ordinal, outputs.lazyMapped(serialize))
    val commandData = Cash.Commands.Issue()
    val command = Command(commandData, listOf(notary.owningKey, DUMMY_BANK_A.owningKey))
    val commandGroup = ComponentGroup(ComponentGroupEnum.COMMANDS_GROUP.ordinal, listOf(command).map { it.value }.lazyMapped(serialize))
    val signersGroup = ComponentGroup(ComponentGroupEnum.SIGNERS_GROUP.ordinal, listOf(command).map { it.signers }.lazyMapped(serialize))

    withTestSerializationEnvIfNotSet {
      val wtx = WireTransaction(listOf(notaryGroup, outputsGroup, commandGroup, signersGroup), PrivacySalt())
      val stx = SignedTransaction(wtx, listOf(createTransactionSignature()))
      val encoded = Json.encode(stx)
      val decoded = Json.decodeValue(encoded, SignedTransaction::class.java)
      assertEquals(decoded, stx)
    }
  }

  private fun createTransactionSignature(): TransactionSignature {
    val txSig = TransactionSignature(
      "message".toByteArray(),
      generatePublicKey(),
      SignatureMetadata(4, 1)
    )
    return txSig
  }

  private fun generatePublicKey(): PublicKey {
    val publicKey = Crypto.generateKeyPair(Crypto.RSA_SHA256).public
    return publicKey
  }

  private fun f(@Suppress("UNUSED_PARAMETER") set: NonEmptySet<String>) {}
}
