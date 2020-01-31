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
package io.bluebank.braid.corda.rest

data class SwaggerInfo (
  val version :String? = "1.0.0",
  val serviceName :String ="",
  val description : String ="",
  val contact :ContactInfo = ContactInfo()
){

  fun withVersion(version:String):SwaggerInfo{
    return copy(version = version)
  }

  fun withServiceName(serviceName:String):SwaggerInfo{
    return copy(serviceName = serviceName)
  }

  fun withDescription(description:String):SwaggerInfo{
    return copy(description = description)
  }

  fun withContact(contact:ContactInfo):SwaggerInfo{
    return copy(contact = contact)
  }

}