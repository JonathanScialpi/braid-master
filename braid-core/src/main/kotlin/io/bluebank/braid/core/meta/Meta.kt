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
package io.bluebank.braid.core.meta

const val DEFAULT_API_MOUNT = "/api/"

fun defaultServiceEndpoint(serviceName: String) =
  "${defaultServiceMountpoint(DEFAULT_API_MOUNT, serviceName)}/braid"

fun defaultServiceEndpoint(rootAPIPath: String, serviceName: String) =
  "${defaultServiceMountpoint(rootAPIPath, serviceName)}/braid"

fun defaultServiceMountpoint(serviceName: String) = "$DEFAULT_API_MOUNT$serviceName"
fun defaultServiceMountpoint(rootAPIPath: String, serviceName: String) =
  "$rootAPIPath$serviceName"

data class ServiceDescriptor(val endpoint: String, val documentation: String) {
  companion object {
    /**
     * Creates a map of [ServiceDescriptor] given the REST root api path and a collection of serviceNames
     *
     * * rootAPIPath - this must terminate with '/' e.g. /api/
     */
    fun createServiceDescriptors(
      rootAPIPath: String,
      serviceNames: Collection<String>
    ): Map<String, ServiceDescriptor> {
      assert(rootAPIPath.endsWith('/')) {
        "$rootAPIPath doesn't end with '/'"
      }
      return serviceNames.map {
        it to ServiceDescriptor(
          defaultServiceEndpoint(rootAPIPath, it),
          "$rootAPIPath$it"
        )
      }.toMap()
    }
  }
}