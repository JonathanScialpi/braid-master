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
package io.bluebank.braid.sample

import io.bluebank.braid.core.annotation.MethodDescription
import io.bluebank.braid.core.annotation.ServiceDescription
import io.vertx.core.Vertx
import rx.Observable
import java.text.SimpleDateFormat
import java.util.*

/**
 * a simple time service
 * This uses publishes "time" events to the event bus
 * Each client of the [time] function listens to the bus
 */
@ServiceDescription("time", "a simple time service")
class TimeService(private val vertx: Vertx) {

  companion object {
    private val timeFormat = SimpleDateFormat("HH:mm:ss")
  }

  init {
    // create a timer publisher to the eventbus
    vertx.setPeriodic(1000) {
      vertx.eventBus().publish("time", timeFormat.format(Date()))
    }
  }

  // N.B. how we can document the return type of a stream
  @MethodDescription(
    returnType = String::class,
    description = "return a stream of time updates"
  )
  fun time(): Observable<String> {
    @Suppress("DEPRECATION")
    return Observable.create { subscriber ->
      val consumer = vertx.eventBus().consumer<String>("time")
      consumer.handler {
        if (subscriber.isUnsubscribed) {
          consumer.unregister()
        } else {
          subscriber.onNext(it.body())
        }
      }
    }
  }
}