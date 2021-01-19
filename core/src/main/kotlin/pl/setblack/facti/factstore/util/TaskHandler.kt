package pl.setblack.facti.factstore.util

import io.vavr.Tuple
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import io.vavr.collection.List
import java.lang.IllegalStateException

/**
 * Task handler - an executor for IO tasks.
 *
 */
interface TasksHandler {
    fun <T> putIOTask(id : String, task : (CompletableFuture<T>)->Unit) : Mono<T>
}

internal class SimpleTaskHandler(private val threads : Int = 1)  : TasksHandler {
    private val ioExecutors = initExecutors()
    override
      fun <T> putIOTask(id : String, task : (CompletableFuture<T>)->Unit) : Mono<T> {
        val promise = CompletableFuture<T>()
        val executor = calcExecutor(id)
        this.ioExecutors[executor].getOrElseThrow{IllegalStateException("$executor")}.submit{
            try {
                task(promise)
            } catch (e : Exception) {
                promise.completeExceptionally(e)
            }
        }
        return Mono.fromFuture(promise)
    }

    private fun calcExecutor(id:String) : Int  {
        val rem =    id.hashCode() % ( threads)
        return if ( rem >= 0) {rem} else {rem + threads}
    }

    private fun initExecutors() = List.range(0, threads).toMap { index ->  Tuple.of(index, Executors.newSingleThreadExecutor()) }

}
