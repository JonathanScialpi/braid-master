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
package io.bluebank.braid

import org.hamcrest.CoreMatchers
import org.junit.Assert.*
import org.junit.Test
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore

class HoconReaderTest{
  companion object{
    val config = HoconReader().read("web-server.conf")
  }

@Test
  fun `read boolean`() {
    assertThat(config.getBoolean("devMode"), equalTo(true))
  }

  @Test
  fun `read config`() {
    assertThat(config.getConfig("rpcSettings"), notNullValue())
  }

  @Test
  fun `read rps address`() {
    assertThat(config.getString("rpcSettings.address"), equalTo("localhost:10006"))
  }

  @Test
  fun `read user in array`() {
    assertThat(config.getConfigList("security.authService.dataSource.users").get(0).getString("user"), equalTo("user1"))
  }

  @Test
  fun `read password in array`() {
    assertThat(config.getConfigList("security.authService.dataSource.users").get(0).getString("password"), equalTo("test"))
  }

  @Test
  fun `read port`() {
    assertThat(config.getString("webAddress"), equalTo("localhost:10007"))
  }

  @Test
  fun `ishould read file`() {
    val read = HoconReader().read(HoconReaderTest::class.java.getResource("/web-server.conf").file)

    assertThat(read.hasPath("webAddress"), equalTo(true))
 }

  @Test
  fun `identify missing properties`() {
    val read = HoconReader().read("missing.conf")

    assertThat(config.hasPath("webAddress"), equalTo(true))
    assertThat(read.hasPath("webAddress"), equalTo(false))
  }
}