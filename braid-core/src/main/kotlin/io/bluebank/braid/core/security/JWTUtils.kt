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
package io.bluebank.braid.core.security

import java.security.KeyStore
import javax.crypto.KeyGenerator

object JWTUtils {
  fun createSimpleJWTKeyStore(password: String): KeyStore {
    val passwordArray = password.toCharArray()
    return createJCEKS(passwordArray).apply {
      addSecretKey("HmacSHA256", "HS256", passwordArray)
      addSecretKey("HMacSHA384", "HS384", passwordArray)
      addSecretKey("HMacSHA512", "HS512", passwordArray)
    }
  }

  private fun createJCEKS(password: CharArray): KeyStore {
    return KeyStore.getInstance("jceks").apply {
      load(null, password)
    }
  }

  private fun KeyStore.addSecretKey(
    algorithm: String,
    alias: String,
    password: CharArray
  ) {
    val keyGen = KeyGenerator.getInstance(algorithm)
    keyGen.init(2048)
    val secretKey = keyGen.generateKey()
    this.setKeyEntry(alias, secretKey, password, null)
  }
}