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
package io.bluebank.braid.corda.serialisation.mixin

//@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "@class")
//@JsonSubTypes(
//    JsonSubTypes.Type(value = SerializedBytes::class),
//    JsonSubTypes.Type(value = OpaqueBytes::class),
//    JsonSubTypes.Type(value = PrivacySalt::class),
//    JsonSubTypes.Type(value = DigitalSignature::class),
//    JsonSubTypes.Type(value = DigitalSignature.WithKey::class),
//    JsonSubTypes.Type(value = SecureHash::class),
//    JsonSubTypes.Type(value = SecureHash.SHA256::class),
//    JsonSubTypes.Type(value = TransactionSignature::class)
//)
//@JsonInclude(JsonInclude.Include.NON_DEFAULT)
//@io.swagger.v3.oas.annotations.media.Schema(
//    type = "object",
//    title = "OpaqueBytes",
//    discriminatorProperty = "@class",
//    discriminatorMapping = [
//      DiscriminatorMapping(value = ".SerializedBytes", schema = SerializedBytes::class),
//      DiscriminatorMapping(value = ".OpaqueBytes", schema = OpaqueBytes::class),
//      DiscriminatorMapping(value = ".PrivacySalt", schema = PrivacySalt::class),
//      DiscriminatorMapping(value = ".DigitalSignature", schema = DigitalSignature::class),
//      DiscriminatorMapping(value = ".DigitalSignature${'$'}WithKey", schema = DigitalSignature.WithKey::class),
//      DiscriminatorMapping(value = ".SecureHash", schema = SecureHash::class),
//      DiscriminatorMapping(value = ".SerializedBytes", schema = SerializedBytes::class),
//      DiscriminatorMapping(value = ".SecureHash${'$'}SHA256", schema = SecureHash.SHA256::class),
//      DiscriminatorMapping(value = ".TransactionSignature", schema = TransactionSignature::class)
//    ],
//    subTypes = [SerializedBytes::class,
//      OpaqueBytes::class,
//      PrivacySalt::class,
//      DigitalSignature::class,
//      DigitalSignature.WithKey::class,
//      SecureHash::class,
//      SecureHash.SHA256::class,
//      TransactionSignature::class]
//)
abstract class OpaqueBytesMixin {
  @get:com.fasterxml.jackson.annotation.JsonIgnore
  abstract val offset: Int
  @get:com.fasterxml.jackson.annotation.JsonIgnore
  abstract val size: Int
}