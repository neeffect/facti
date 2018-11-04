package pl.setblack.facti.factstore.file

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.vavr.jackson.datatype.VavrModule
import org.apache.commons.io.FileUtils
import pl.setblack.facti.factstore.repo.DirectControl
import pl.setblack.facti.factstore.util.TasksHandler
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

open class DirBasedStore<ID, DIRDATA>(
        protected val basePath: Path,
        protected val clock: Clock,
        protected val tasksHandler: TasksHandler): DirectControl {
    protected val aggregates = ConcurrentHashMap<ID, DirState<DIRDATA>>()



    protected val mapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(Jdk8Module())
            .registerModule(JavaTimeModule())
            .registerModule(VavrModule())
            .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
            .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)

    //ook
    protected fun initializeAggregateDir(id: ID, initialData: DIRDATA): DirState<DIRDATA> =
            DirState<DIRDATA>(findFolder(id), initialData, ReentrantLock())

    //ook
    protected fun findAggregateStore(id: ID, initialData: DIRDATA): DirState<DIRDATA> =
            aggregates.computeIfAbsent(id) { initializeAggregateDir(it, initialData) }

    //half ok
    //create directory is sync - but we are only using it inside computeifabsent
    protected fun findFolder(id: ID): Mono<Path> =
            Mono.fromSupplier {
                val folderName = id.toString()
                val aggregateFolderPath = basePath.resolve(folderName)
                if (Files.exists(aggregateFolderPath) && Files.isDirectory(aggregateFolderPath)) {
                    aggregateFolderPath
                } else {
                    Files.createDirectory(aggregateFolderPath)
                }
            }

    override fun deleteAll() {
        this.shutdown()
        val filesInside = Files.newDirectoryStream(basePath)
        filesInside.forEach { file -> FileUtils.deleteDirectory(file.toFile()) }
    }

    override fun restart() {
        this.shutdown()
    }

    override fun shutdown() {
        println("clearing bo...")
        this.aggregates.clear()//flush streams
    }


    protected fun idString(id: ID):String = java.lang.String.valueOf(id)

}

data class DirState<DIRDATA>(
        val aggregatePath: Mono<Path>,
        val dirdata: DIRDATA,
        /*val nextSnapshotNumber: Int = 0,
        val currentEvent: Long = 1,

        val writeEventStream: Mono<Option<Writer>> = Mono.just(Option.none()),*/
        val lock: Lock) {
    init {
        //println("dirstate create with ${lock}")
        DebugSwin.debugStore.put(lock, Swinia("", Exception("tuta")))
    }
}


object DebugSwin {
    fun debug() {
        debugStore.values.forEach {
            println("**************")
            it.whereCreated.printStackTrace()
        }
    }

    val debugStore = ConcurrentHashMap<Lock, Swinia>()

}

data class Swinia( val path: String, val whereCreated : Exception)


