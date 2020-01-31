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

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import io.vertx.ext.auth.User
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Test

class NetworkServiceTest {

  @Test
  fun shouldListSimpleNodeInfo() {
    //   val certFactory = CertificateFactory.getInstance("X509")
    //   val certPath = certFactory.generateCertPath(asList())

    val partyAndCertificate = mock<PartyAndCertificate> { }

    val addresses = listOf(NetworkHostAndPort.parse("localhost:123"))
    val legalIdentitiesAndCerts = listOf(partyAndCertificate)
    val nodeInfo = NodeInfo(addresses, legalIdentitiesAndCerts, 1, 2)
    val ops = mock<CordaServicesAdapter> {
      on { networkMapSnapshot() } doReturn listOf(nodeInfo)
      on { nodeInfo() } doReturn nodeInfo
    }

    fun getAdapter(@Suppress("UNUSED_PARAMETER") user: User?): NetworkMapServiceAdapter {
      return ops
    }

    val user = mock<User> {}

    val network = RestNetworkMapService(::getAdapter)
    val simpleInfo = network.myNodeInfo(user)
    assertThat(simpleInfo.addresses, `is`(addresses))
    assertThat(simpleInfo.legalIdentities.size, `is`(1))
  }
}