package net.kmxz.votive

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

typealias VAny = Any
typealias RAny = Any

class Votive<V: VAny, R: RAny> {

    companion object {

        /**
         * Exactly similar as Promise.all in JavaScript
         *
         * @param iterable Votives that awaits to be all-resolved
         * @return A new votive which will resolve after votives are resolved, or reject as soons as one votive rejects
         */
        fun <Vstatic: VAny, Rstatic: RAny> all(iterable: Iterable<Votive<out Vstatic, out Rstatic>>): Votive<Iterable<Vstatic>, Rstatic> = Votive({ res, rej ->
            iterable.forEach { single ->
                single.thenSimple<Unit, Unit>({
                    if (iterable.all { it.status is Status.Fulfilled }) {
                        res(iterable.map { (it.status as Status.Fulfilled).value })
                    }
                }, rej)
            }
        })

        /**
         * Exactly similar as Promise.race in JavaScript
         *
         * @param iterable Votives that awaits to be resolved
         * @return A new votive which will resolve or reject as soon as one of the votives resolves or rejects
         */
        fun <Vstatic: VAny, Rstatic: RAny> race(iterable: Iterable<Votive<out Vstatic, out Rstatic>>): Votive<Vstatic, Rstatic> = Votive({ res, rej ->
            iterable.forEach { single ->
                single.thenSimple<Unit, Unit>(res, rej)
            }
        })
        /**
         * Exactly similar as Promise.resolve in JavaScript
         *
         * @param value The value to be resolved
         * @return A promise resolved with the supplied value
         */
        fun <Vstatic: VAny, Rstatic: RAny> resolve(value: Vstatic) = Votive<Vstatic, Rstatic>({ res, _ ->
            res(value)
        })

        /**
         * Exactly similar as Promise.resolve in JavaScript
         *
         * @param value The value to be resolved
         * @return A promise resolved with the supplied value
         */
        fun <Vstatic: VAny, Rstatic: RAny> resolve(votive: Votive<Vstatic, Rstatic>) = votive.thenSimple({ it })

        /**
         * Exactly similar as Promise.reject in JavaScript
         *
         * @param reason The reason to reject
         * @return A promise rejected with the supplied reason
         */
        fun <Vstatic: VAny, Rstatic: RAny> reject(reason: Rstatic) = Votive<Vstatic, Rstatic>({ _, rej ->
            rej(reason)
        })

    }

    private sealed class Status<V: VAny, R: RAny> {
        internal data class Pending<V: VAny, R: RAny>(val queue: ConcurrentLinkedQueue<QueueItem<V, R, out VAny, out RAny>> = ConcurrentLinkedQueue()): Status<V, R>()
        internal data class Fulfilled<V: VAny, R: RAny>(val value: V): Status<V, R>()
        internal data class Rejected<V: VAny, R: RAny>(val reason: R): Status<V, R>()
    }

    private class QueueItem<in Vin: VAny, in Rin: RAny, Vout: VAny, Rout: RAny> (
        private val outVotive: Votive<Vout, Rout>,
        private val onFulfilled: (Vin) -> Votive<out Vout, out Rout>,
        private val onRejected: (Rin) -> Votive<out Vout, out Rout>
    ) {
        fun resolveInternal(value: Vin) {
            notifyChained(onFulfilled(value))
        }

        fun rejectInternal(reason: Rin) {
            notifyChained(onRejected(reason))
        }

        private fun notifyChained(result: Votive<out Vout, out Rout>) {
            result.thenSimple<Unit, Unit>(outVotive::resolveInternal, outVotive::rejectInternal)
        }
    }

    private var status: Status<V, R> = Status.Pending<V, R>()

    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    private fun updateStatus(newStatus: Status<V, R>): Collection<QueueItem<V, R, out VAny, out RAny>>? {
        val writeLock = this.lock.writeLock()
        writeLock.lock()
        val oldStatus = this.status as? Status.Pending<V, R>
        if (oldStatus == null) {
            writeLock.unlock()
            return null
        }
        this.status = newStatus
        writeLock.unlock()
        return oldStatus.queue
    }

    private fun resolveInternal(value: V) {
        this.updateStatus(Status.Fulfilled(value))?.forEach { queueItem ->
            queueItem.resolveInternal(value)
        }
    }

    private fun rejectInternal(reason: R) {
        this.updateStatus(Status.Rejected(reason))?.forEach { queueItem ->
            queueItem.rejectInternal(reason)
        }
    }

    private constructor()

    /**
     * Exactly like Promise constructor in JavaScript
     *
     * @param executor A function that is passed with the arguments resolve and reject
     */
    constructor(executor: (((V) -> Unit, (R) -> Unit) -> Unit)) {
        executor(this::resolveInternal, this::rejectInternal)
    }

    /**
     * Similar as Promise.prototype.then in JavaScript, except that you cannot reject by throwing
     * Use this one if you are not rejecting anyway
     * Renamed because JVM won't allow methods with the same name
     *
     * @param onFulfilled Callback to execute when promise is resolved
     * @return a new promise, resolved with value returned from onFulFilled
     */
    fun <Vout: VAny> thenSimple(onFulfilled: (V) -> Vout): Votive<Vout, R> = then({ v -> Votive.resolve<Vout, R>(onFulfilled(v)) })

    /**
     * Similar as Promise.prototype.then in JavaScript, except that you cannot reject by throwing
     * Use this one if you may reject the promise by returning a rejected promise
     *
     * @param onFulfilled Callback to execute when promise is resolved
     * @return a new promise, resolved or rejected with value returned from onFulFilled
     */
    fun <Vout: VAny> then(onFulfilled: (V) -> Votive<Vout, R>): Votive<Vout, R> = then(onFulfilled, Votive.Companion::reject)

    /**
     * Similar as Promise.prototype.then in JavaScript, except that you cannot reject by throwing
     * Use this one if you are not rejecting anyway
     * Renamed because JVM won't allow methods with the same name
     *
     * @param onFulfilled Callback to execute when promise is resolved
     * @param onRejected Callback to execute when promise is rejected
     * @return a new promise, resolved with value returned from onFulFilled or onRejected
     */
    fun <Vout: VAny, Rout: RAny> thenSimple(onFulfilled: (V) -> Vout, onRejected: (R) -> Vout): Votive<Vout, Rout> = then({ v -> Votive.resolve<Vout, Rout>(onFulfilled(v)) }, { r -> Votive.resolve<Vout, Rout>(onRejected(r)) })

    /**
     * Similar as Promise.prototype.then in JavaScript, except that you cannot reject by throwing
     * Use this one if you may reject the promise by returning a rejected promise
     *
     * @param onFulfilled Callback to execute when promise is resolved
     * @param onRejected Callback to execute when promise is rejected
     * @return a new promise, resolved or rejected with value returned from onFulFilled or onRejected
     */
    fun <Vout: VAny, Rout: RAny> then(onFulfilled: (V) -> Votive<Vout, Rout>, onRejected: (R) -> Votive<Vout, Rout>): Votive<Vout, Rout> {
        var readLock: Lock? = null
        if (this.status is Status.Pending) {
            // no write lock needed, as queue is concurrent
            // read lock needed to prevent that status changes to fulfilled/rejected
            // conditional locking is enough, as status will never change from fulfilled/rejected to pending
            readLock = this.lock.readLock()
            readLock.lock()
        }
        val status = this.status // make compiler's smart cast happy
        when (status) {
            is Status.Pending -> {
                val newVotive = Votive<Vout, Rout>()
                val item = QueueItem(newVotive, onFulfilled, onRejected)
                status.queue.add(item)
                readLock?.unlock()
                return newVotive
            }
            is Status.Fulfilled -> {
                readLock?.unlock()
                return onFulfilled(status.value)
            }
            is Status.Rejected -> {
                readLock?.unlock()
                return onRejected(status.reason)
            }
        }
    }

    /**
     * Similar as Promise.prototype.catch in JavaScript, except that you cannot reject by throwing
     * Use this one if you are not rejecting anyway
     * Renamed because JVM won't allow methods with the same name
     *
     * @param onRejected Callback to execute when promise is rejected
     * @return a new promise, resolved with value returned from onRejected
     */
    fun catchSimple(onRejected: (R) -> V): Votive<V, R> = thenSimple({ it }, onRejected)

    /**
     * Similar as Promise.prototype.catch in JavaScript, except that you cannot reject by throwing
     * Use this one if you may reject the promise by returning a rejected promise
     *
     * @param onRejected Callback to execute when promise is rejected
     * @return a new promise, resolved or rejected with value returned from onRejected
     */
    fun <Rout: RAny> catch(onRejected: (R) -> Votive<V, Rout>): Votive<V, Rout> = then(Votive.Companion::resolve, onRejected)

}