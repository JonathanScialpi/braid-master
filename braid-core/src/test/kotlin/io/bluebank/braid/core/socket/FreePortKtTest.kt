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
package io.bluebank.braid.core.socket

import org.junit.Test
import java.net.ServerSocket
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreePortKtTest {
  @Test
  fun `find consecutive free ports`() {
    val count = 5
    val base = findConsecutiveFreePorts(count)
    val range = base until base + count
    assertTrue { isPortRangeFree(range) }
    ServerSocket(base).use { assertFalse { isPortRangeFree(range) } }
  }
}