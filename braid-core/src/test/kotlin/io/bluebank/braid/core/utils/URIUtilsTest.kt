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
package io.bluebank.braid.core.utils

import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals

class URIUtilsTest {
  companion object {
    const val CORDAPP_NAME = "my-cordapp"
    const val CORDAPP_VERSION = "1.1"
    const val CORDAPP_JAR = "$CORDAPP_NAME-$CORDAPP_VERSION.jar"
  }

  @Test
  fun `given a valid cordapp path that we can extract the cordapp name`() {
    val cordappName = URL("file:///foo/bar/$CORDAPP_JAR").toCordappName()
    assertEquals(CORDAPP_NAME, cordappName)
  }

  @Test
  fun `given a test folder we use that as the cordapp name`() {
    val cordappName = URL("file:///foo/bar/").toCordappName()
    assertEquals("bar", cordappName)
  }

  @Test
  fun `given an invalid cordapp file name we still generate a sensible name`() {
    val cordappName = URL("file:///foo/bar/$CORDAPP_NAME.jar").toCordappName()
    assertEquals("$CORDAPP_NAME-jar", cordappName)
  }
}