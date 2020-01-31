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
package io.bluebank.braid.corda.server

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class VersionNameRemoverTest {
  @Test
  fun `that we can remove the version number from a name with a version number`() {
    val name = "hello-world"
    val withVersion = "$name-12.432.23423"
    assertThat(name, equalTo(withVersion.removeVersion()))
  }

  @Test
  fun `that a string without a version number will be returned unchanged`() {
    val name = "hello-world"
    assertThat(name, equalTo(name.removeVersion()))
  }
}