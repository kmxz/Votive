# Votive

Another promise library for Kotlin (on JVM).

## Motive

- Only one existing promise [implementation](https://github.com/mplatvoet/kovenant) in Kotlin, which is quite complicated. In addition, its API diverses too much from promise implementations in JavaScript.

- Existing promise implementations on JVM followed the practice of "rejecting by throwing". This is, however, not favored (see "Differences from Promise/A+" section below).

## API (`Votive` class)

For details, you may read KDoc inside the code.

Class `Votive<V, R>` take two type parameters, where `V` is value type (when fulfilled/resolved), `R` is the reason type (when rejected). It corresponds to `Promise` in JavaScript.

### Constructor

- `Votive(executor: (((V) -> Unit, (R) -> Unit) -> Unit))`. Exactly the same as `Promise` constructor in JavaScript.

### Static methods (in companion object)

- `all(iterable: Iterable<Votive>): Votive`. Exactly the same as `Promise.all` in JavaScript.
- `race(iterable: Iterable<Votive>): Votive`. Exactly the same as `Promise.race` in JavaScript.
- `resolve(value: V): Votive`. Exactly the same as `Promise.resolve` in JavaScript.
- `resolve(votive: Votive): Votive`. Exactly the same as `Promise.resolve` in JavaScript.
- `reject(reason: R): Votive`. Exactly the same as `Promise.reject` in JavaScript.

### Instance methods

- `thenSimple(onFulfilled: (V) -> Vout): Votive<Vout, R>`. Similar as `Promise.prototype.then` in JavaScript.
- `then(onFulfilled: (V) -> Votive<Vout, R>): Votive<Vout, R>`. Similar as `Promise.prototype.then` in JavaScript.
- `thenSimple(onFulfilled: (V) -> Vout, onRejected: (R) -> Vout): Votive<Vout, Rout>`. Similar as `Promise.prototype.then` in JavaScript.
- `then(onFulfilled: (V) -> Votive<Vout, Rout>, onRejected: (R) -> Votive<Vout, Rout>): Votive<Vout, Rout>`. Similar as `Promise.prototype.then` in JavaScript.
- `catchSimple(onRejected: (R) -> V): Votive<V, R>`. Similar as `Promise.prototype.catch` in JavaScript.
- `catch(onRejected: (R) -> Votive<V, Rout>): Votive<V, Rout>`. Similar as `Promise.prototype.catch` in JavaScript.

Use "simple" methods is you are not rejecting anyway; use methods without "simple" if you may reject the promise by returning a rejected promise.

## Example

    fun setTimeout(runnable: () -> Unit, delay: Int) {
        Thread {
            Thread.sleep(delay.toLong())
            runnable()
        }.start()
    }
    
    fun main(args : Array<String>) {
        val p1 = Votive<String, String>({ res, rej -> setTimeout({ res("[p1]") }, 600) })
        val p2 = Votive<String, String>({ res, rej -> setTimeout({ rej("[p2]") }, 1000) })
        val p3 = Votive<String, String>({ res, rej -> setTimeout({ res("[p3]") }, 1800) })
        val p4 = Votive<String, String>({ res, rej -> setTimeout({ res("[p4]") }, 200) })
        Votive.race(listOf(p1, p2, p3, p4)).thenSimple(::println)
        Votive.all(listOf(p1, p2, p3, p4)).thenSimple<Unit, Unit>(::println, ::println)
        p3.then({ Votive.reject<String, String>("Oops") }).catchSimple { it + "Really?" }.thenSimple(::println)
    }

## Differences from Promise/A+

- As mentioned above, "rejecting by throwing" is not allowed. Any throws will not be caught. To reject, use [the trick](http://azu.github.io/promises-book/#not-throw-use-reject) of returning `Promise.reject(reason)` instead of `throw reason`. Reasons:
  - In JVM, exceptions are expensive.
  - Unlike in JavaScript where anything can be thrown, JVM only allows throwing subclasses of `Throwable`.
  - If "rejecting by throwing" is used, the business logic (rejects) and software error (throws) are mixed together.
  - If "rejecting by throwing" is used, the rejection callback (`onRejected`) has to take `Throwable` as parameter, instead of a more specific type.

- The rule of "`onFulfilled` or `onRejected` must not be called until the execution context stack contains only platform code" will NOT be respected. They will be called immediately on the thread which resolve/reject is done.  Reasons:
  - It's caller's own responsibility to take care of executing order.
  - On JVM, there are no reliable way to put off callbacks appropriately without affecting portability.

## Threading

Unlike some promise libraries who automatically execute asynchronous tasks on new threads, this library does not create new threads automatically. This is because some libraries for specific tasks already take care of creating threads.

However, this library itself it totally thread-safe. In other words, you can share a votive (promise) across threads and do whatever you want.
