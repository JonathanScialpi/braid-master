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
package io.bluebank.braid.corda.serialisation.serializers

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import io.bluebank.braid.corda.serialisation.mixin.*
import io.bluebank.braid.core.json.BraidJacksonInit
import io.vertx.core.json.Json
import net.corda.client.jackson.JacksonSupport
import net.corda.core.CordaThrowable
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.vault.ColumnPredicate
import net.corda.core.node.services.vault.CriteriaExpression
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TraversableTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate

/**
 * If you add to this file, please also add to CustomModelConverter forcorrect swagger generation
 */
object BraidCordaJacksonInit {

  init {
    BraidJacksonInit.init()
    // we reuse the jackson support from corda, replacing those that are not flexible enough for
    // dynamic languages
    @Suppress("DEPRECATION") val sm = SimpleModule("io.swagger.util.DeserializationModule")
      .addAbstractTypeMapping(AbstractParty::class.java, Party::class.java)
      .addSerializer(SecureHash::class.java, SecureHashSerializer)
      .addSerializer(SecureHash.SHA256::class.java, SecureHashSerializer)
      .addDeserializer(SecureHash::class.java, SecureHashDeserializer())
      .addDeserializer(SecureHash.SHA256::class.java, SecureHashDeserializer())

      // For ed25519 pubkeys
      // TODO: Fix these
//          .addSerializer(EdDSAPublicKey::class.java, JacksonSupport.PublicKeySerializer)
//          .addDeserializer(EdDSAPublicKey::class.java, JacksonSupport.PublicKeyDeserializer)

      // For NodeInfo
      // TODO this tunnels the Kryo representation as a Base58 encoded string. Replace when RPC supports this.
      .addSerializer(NodeInfo::class.java, JacksonSupport.NodeInfoSerializer)
      .addDeserializer(NodeInfo::class.java, JacksonSupport.NodeInfoDeserializer)
      .setMixInAnnotation(SerializedBytes::class.java, SerializedBytesMixin::class.java)
      .setMixInAnnotation(OpaqueBytes::class.java, OpaqueBytesMixin::class.java)
      // For X.500 distinguished names
      .addDeserializer(CordaX500Name::class.java, JacksonSupport.CordaX500NameDeserializer)
      .addSerializer(CordaX500Name::class.java, JacksonSupport.CordaX500NameSerializer)

      // Mixins for transaction types to prevent some properties from being serialized
//      .setMixInAnnotation(SignedTransaction::class.java, JacksonSupport.SignedTransactionMixin::class.java)
      .setMixInAnnotation(SignedTransaction::class.java, SignedTransactionMixin::class.java)
      // Caused by: io.vertx.core.json.EncodeException: Failed to encode as JSON: net.corda.core.transactions.WireTransaction cannot be cast to net.corda.core.transactions.NotaryChangeWireTransaction (through reference chain: net.corda.finance.flows.AbstractCashFlow$Result["stx"]->net.corda.core.transactions.SignedTransaction["notaryChangeTx"])
      .setMixInAnnotation(WireTransaction::class.java, WireTransactionMixin::class.java)
      //.setMixInAnnotation(FungibleAsset::class.java, FungibleAssetMixin::class.java)
      .setMixInAnnotation(TraversableTransaction::class.java, TraversableTransactionMixin::class.java)
      .setMixInAnnotation(TimeWindow::class.java, TimeWindowMixin::class.java)
      .setMixInAnnotation(Throwable::class.java, ThrowableMixin::class.java)
      .setMixInAnnotation(CordaThrowable::class.java, CordaThrowableMixin::class.java)
      .setMixInAnnotation(NonEmptySet::class.java, IgnoreTypeMixin::class.java)
      .setMixInAnnotation(ByteSequence::class.java, ByteSequenceMixin::class.java)
      .setMixInAnnotation(ProgressTracker::class.java, IgnoreTypeMixin::class.java)
      .setMixInAnnotation(ContractState::class.java, ContractStateMixin::class.java)
      .setMixInAnnotation(QueryCriteria::class.java, QueryCriteriaMixin::class.java)
    //  .setMixInAnnotation(QueryCriteria.VaultCustomQueryCriteria::class.java, VaultCustomQueryCriteriaMixin::class.java)
      .setMixInAnnotation(CriteriaExpression::class.java, CriteriaExpressionMixin::class.java)
      .setMixInAnnotation(ColumnPredicate::class.java, ColumnPredicateMixin::class.java)
      .setMixInAnnotation(PageSpecification::class.java, PageSpecificationMixin::class.java)
      .setMixInAnnotation(TransactionState::class.java, TransactionStateMixin::class.java)
      .setMixInAnnotation(Issued::class.java, IssuedMixin::class.java)
      .addSerializer(X509Certificate::class.java, X509Serializer())
      .addDeserializer(X509Certificate::class.java, X509Deserializer())
      .addSerializer(CertPath::class.java, CertPathSerializer())
      .addDeserializer(CertPath::class.java, CertPathDeserializer())
      .addDeserializer(NonEmptySet::class.java, NonEmptySetDeserializer())
      .addSerializer(PublicKey::class.java, PublicKeySerializer())
      .addDeserializer(PublicKey::class.java, PublicKeyDeserializer())
      // For Amount
      // we do not use the Corda amount serialisers
      .addSerializer(Amount::class.java, AmountSerializer())
      .addDeserializer(Amount::class.java, AmountDeserializer())
      //     .addSerializer(Currency::class.java, CurrencySerializer())          likely to cause issues distinuishing strings from currency in Amount and Issuer class
      //     .addDeserializer(Currency::class.java, CurrencyDeserializer())
      .addSerializer(Issued::class.java, IssuedSerializer())
      .addDeserializer(Issued::class.java, IssuedDeserializer())
      .addDeserializer(AutomaticPlaceholderConstraint::class.java, SingletonDeserializer(AutomaticPlaceholderConstraint))

    listOf(Json.mapper, Json.prettyMapper, io.swagger.v3.core.util.Json.mapper()).forEach {
      it.registerModule(sm)
        .registerModule(ParameterNamesModule())
        .registerModule(Jdk8Module())
        .registerModule(JavaTimeModule())
    };
  }

  fun init() {
    // automatically initialise the static constructor
  }
}

