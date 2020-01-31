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
package io.bluebank.braid.corda.server.progress

import io.bluebank.braid.corda.server.flow.FlowInitiator
import io.bluebank.braid.core.logging.loggerFor
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.vertx.core.eventbus.EventBus
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.ext.web.RoutingContext
import javax.ws.rs.core.MediaType

class TrackerHandler(private val eventBus: EventBus) {

  companion object {
    var log = loggerFor<TrackerHandler>()
  }

  @Operation(
    description = "Connect to the Progress Tracker. " +
      "This call will return chunked responses of all progress trackers",
    responses = [ApiResponse(
      
      content = arrayOf(
                Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = Schema(implementation = ProgressNotification::class),
                 examples =  [
                   ExampleObject(name = "boo", value = "exampleError",
                     summary = "example of an error", externalValue = "{  error:\"anError\" }")
                 ]
                )))
    ]
  )
  fun handle(ctx: RoutingContext) {
    ctx.response()
      .setChunked(true)
      .putHeader("Content-Type","application/json; charset=utf-8")
      .putHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
      .putHeader("Pragma", "no-cache")
      .putHeader(HttpHeaders.EXPIRES, "0")

    val flowProgress = eventBus.consumer<String>(FlowInitiator.TOPIC){
      log.trace(it.body())
      ctx.response().write(it.body())
    }

    ctx.response().closeHandler{
      flowProgress.unregister()
      ctx.response().end();   // not sure if this is needed
    }
  }

}