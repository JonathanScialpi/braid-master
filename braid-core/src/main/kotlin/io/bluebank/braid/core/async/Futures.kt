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
package io.bluebank.braid.core.async

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Future.succeededFuture
import rx.Observable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

fun <T> Future<T>.getOrThrow(): T {
  val latch = CountDownLatch(1)

  this.setHandler {
    latch.countDown()
  }

  latch.await()
  if (this.failed()) {
    throw this.cause()
  }
  return this.result()
}

fun <T> Future<T>.onSuccess(fn: (T) -> Unit): Future<T> {
  val result = future<T>()
  setHandler {
    try {
      if (it.succeeded()) {
        fn(it.result())
      }
      result.handle(it)
    } catch (err: Throwable) {
      result.fail(err)
    }
  }
  return result
}

fun <T> Future<T>.catch(fn: (Throwable) -> Unit): Future<T> {
  val result = future<T>()
  setHandler {
    try {
      if (it.failed()) {
        fn(it.cause())
      }
      result.handle(it)
    } catch (err: Throwable) {
      result.fail(err)
    }
  }
  return result
}

fun <T> Future<T>.finally(fn: (AsyncResult<T>) -> Unit): Future<T> {
  val result = future<T>()
  setHandler {
    try {
      fn(it)
      result.handle(it)
    } catch (err: Throwable) {
      result.fail(err)
    }
  }
  return result
}

@JvmName("allTyped")
fun <T> all(vararg futures: Future<T>): Future<List<T>> {
  return futures.toList().all()
}

@Suppress("UNCHECKED_CAST")
fun all(vararg futures: Future<*>): Future<List<*>> {
  return (futures.toList() as List<Future<Any>>).all() as Future<List<*>>
}

fun <T> List<Future<T>>.all(): Future<List<T>> {
  if (this.isEmpty()) return succeededFuture(emptyList())
  val results = mutableMapOf<Int, T>()
  val fResult = future<List<T>>()
  val countdown = AtomicInteger(this.size)
  this.forEachIndexed { index, future ->
    future.setHandler { ar ->
      when {
        fResult.failed() -> {
          // we received a result after the future was deemed failed. carry on.
        }
        ar.succeeded() -> {
          results[index] = ar.result()
          if (countdown.decrementAndGet() == 0) {
            fResult.complete(results.entries.sortedBy { it.key }.map { it.value })
          }
        }
        else -> {
          // we've got a failed future - report it
          fResult.fail(ar.cause())
        }
      }
    }
  }
  return fResult
}

inline fun <T> withFuture(fn: (Future<T>) -> Unit): Future<T> {
  val result = future<T>()
  fn(result)
  return result
}

fun <T> Future<T>.mapUnit(): Future<Unit> = this.map { Unit }

fun <T> Future<T>.assertFails(): Future<Unit> {
  val result = future<Unit>()
  setHandler {
    when {
      it.succeeded() -> result.fail("did not fail")
      else -> result.complete(Unit)
    }
  }
  return result
}

fun <T> Observable<T>.toFuture(): Future<T> {
  val future = future<T>()
  this.single().subscribe(future::complete, future::fail)
  return future
}