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
package com.template

import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test

class FlowTests {
  lateinit var network: MockNetwork
  lateinit var a: StartedMockNode
  lateinit var b: StartedMockNode

  @Before
  fun setup() {
    network = MockNetwork(listOf("com.template"))
    a = network.createNode()
    b = network.createNode()
    network.runNetwork()
  }

  @After
  fun tearDown() {
    network.stopNodes()
  }

  @Test
  fun `dummy test`() {

  }
}