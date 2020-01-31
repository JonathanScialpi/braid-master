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
package io.bluebank.braid.corda.integration.cordapp

import io.bluebank.braid.core.annotation.ServiceDescription
import io.vertx.core.Future
import net.corda.core.identity.CordaX500Name
import rx.Observable
import rx.schedulers.Schedulers
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ServiceDescription("my-service", "A simple service for testing braid")
class CustomService {

  private val scheduler = Schedulers.from(Executors.newFixedThreadPool(1))

  fun add(lhs: Int, rhs: Int) = lhs + rhs

  fun badjuju(): Int {
    throw RuntimeException("I threw an exception")
  }

  fun asyncResult(): Future<String> {
    val result = Future.future<String>()
    streamedResult().first().single().subscribe { result.complete(it.toString()) }
    return result
  }

  fun streamedResult(): Observable<Int> {
    return Observable.range(0, 10, scheduler).delay(1, TimeUnit.MILLISECONDS)
  }

  fun infiniteStream(): Observable<Long> {
    return Observable.interval(1, TimeUnit.SECONDS)
  }

  fun streamedResultThatFails(): Observable<Int> {
    return Observable.range(0, 10, scheduler)
      .doOnNext { if (it == 5) throw RuntimeException("boom") }
  }

  // function to test https://gitlab.com/bluebank/braid/merge_requests/76
  fun slowData(): List<SlowDataItem> {
    Thread.sleep((Math.random() * 1000).toLong())
    return (1..1000).map { SlowDataItem(Date()) }
  }

  fun createDao(
    daoName: String,
    minimumMemberCount: Int,
    strictMode: Boolean,
    notaryName: CordaX500Name
  ): Future<DaoState> {
    return Future.succeededFuture(
      DaoState(
        daoName,
        minimumMemberCount,
        strictMode,
        notaryName
      )
    )
  }

  fun useInstant(instant: Instant): String {
    return instant.toString()
  }

  fun useDate(date: Date): String {
    return date.toString()
  }
}

data class SlowDataItem(val date: Date)

data class DaoState(
  val name: String,
  val minimumMemberCount: Int,
  val strictMode: Boolean,
  val notaryName: CordaX500Name
)