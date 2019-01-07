package pl.setblack.facti.factstore.file

import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.vavr.control.Option
import pl.setblack.facti.factstore.DevNull
import pl.setblack.facti.factstore.Fact
import pl.setblack.facti.factstore.ReadSideProcessor
import pl.setblack.facti.factstore.repo.FactStore
import pl.setblack.facti.factstore.repo.LoadedFact
import pl.setblack.facti.factstore.repo.SavedFact
import pl.setblack.facti.factstore.util.TasksHandler
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.SynchronousSink
import reactor.core.publisher.toFlux
import java.io.BufferedReader
import java.io.IOException
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * TODO -  not tests for flushing stream events upon shutdown()
 *
 * TODO - simplyfy readSidee, remove idfact, olny readSide per one aggregate supported, introduce concept of
 * sending facts to uuper factstore
 */
class FileFactStore<ID, FACT : Fact<*>>(
        basePath: Path,
        clock: Clock,
        tasksHandler: TasksHandler,

        val idFromString: (String) -> ID) : DirBasedStore<ID, EventDir>
(basePath, clock, tasksHandler), FactStore<ID, FACT, Unit> {
    private val initial = EventDir()

    //ook
    override fun persist(id: ID, ev: FACT): Mono<SavedFact<FACT, Unit>> = Mono.defer {
        ensureEventWriter(id).flatMap { writableStore ->
            //val eventString = mapper.writeValueAsString(ev)
            val factNode: JsonNode = mapper.valueToTree(ev)
            val eventClass = ev.javaClass.name
            tryWrite(id, ev, factNode, eventClass, 25, writableStore.state, writableStore.eventWriter)
                    /*.map {
                        readSide.processFact(id, ev, it)
                        it
                    }*/
        }
    }

    override
    fun loadFacts(id: ID, offset: Long): Flux<SavedFact<FACT, Unit>> {
        val events = findFolder(id).toFlux().flatMap { aggregatePath ->
            val lastEventCallback = { eventId: Long ->
                this.aggregates.computeIfPresent(id) { _, oldDirState ->
                    oldDirState.copy(
                            dirdata = oldDirState.dirdata.copy(nextEventNumber = eventId + 1,
                                    writeEventStream = Mono.just(Option.none()))
                    )
                }
                Unit
            }
            val restoredEvents = Flux.generate<SavedFact<FACT, Unit>>(
                    EventsReader(lastEventCallback, offset, aggregatePath, mapper))
            restoredEvents
        }
        return events
    }

    //nook
    override
    fun roll(id: ID): Mono<Long> = Mono.defer {
        val writer = getExistingWriter(id)
        writer.flatMap { writableDirOption ->

            this.tasksHandler.putIOTask<Long>(idString(id)) { completion ->
                try {

                    writableDirOption.map { writableDir ->
                        val store = writableDir.state
                        val newStore = store.copy(
                                dirdata = store.dirdata.copy(writeEventStream = Mono.just(Option.none())))

                        val locked = writableDir.state.lock.tryLock()
                        if (locked) {

                            try {
                                val replacement = this.aggregates.replace(id, store, newStore)
                                if (replacement) {
                                    writableDir.eventWriter.close()
                                    completion.complete(newStore.dirdata.nextEventNumber)
                                } else {
                                    //println("disaster unhandled perfectly")//TODO not perfect
                                    completion.complete(findAggregateStore(id, initial).dirdata.nextEventNumber)
                                }
                            } finally {

                                writableDir.state.lock.unlock()
                            }
                        } else {
                            completion.complete(findAggregateStore(id, initial).dirdata.nextEventNumber)
                        }
                    }.getOrElse {
                        completion.complete(findAggregateStore(id, initial).dirdata.nextEventNumber)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    completion.completeExceptionally(e)
                }
            }
        }
    }

    override fun loadAll(lastFact: Unit): Flux<LoadedFact<ID, FACT>> {
        val directories = Files.newDirectoryStream(basePath) { path ->
            Files.isDirectory(path)

        }
        val factStream = directories.map {
            val aggrgateFolderName = it.fileName.toString()
            val aggegrateID = idFromString(aggrgateFolderName)
            loadFacts(aggegrateID, 0).map {
                LoadedFact(aggegrateID, it.fact)
            }
        }
        return Flux.concat(factStream)
    }

    private fun getExistingWriter(id: ID): Mono<Option<WritableDirState>> {
        val store = findAggregateStore(id, initial)
        return store.aggregatePath
                .flatMap {
                    store.dirdata.writeEventStream.map {
                        it.map {
                            val storeWritable = findAggregateStore(id, initial) //TODO is this call is  needed?
                            WritableDirState(it, storeWritable)
                        }
                    }
                }
//        return findAggregateStore(id, initial).aggregatePath
//                .flatMap { aggregatePath ->
//                    findAggregateStore(id, initial).dirdata.writeEventStream.map {
//                        it.map {
//                            val store = findAggregateStore(id, initial)
//                            WritableDirState(it, store)
//                        }
//                    }
//                }
    }

    //nok
    //simplify
    private fun ensureEventWriter(id: ID): Mono<WritableDirState> {
        val store = findAggregateStore(id, initial)
        val result = store.aggregatePath
                .flatMap { aggregatePath ->
                    store.dirdata.writeEventStream.flatMap { writer ->
                        writer
                                .map {
                                    Mono.just(WritableDirState(it, store))
                                }
                                .getOrElse {
                                    val filePath = aggregatePath.resolve(eventFileSuffix(store.dirdata.nextEventNumber))

                                    try {
                                        crateFactsWriter(filePath, store, id)

                                    } catch (e: IOException) {
                                        e.printStackTrace()
                                        return@getOrElse ensureEventWriter(id)
                                        //throw RuntimeException(e)
                                    }

                                }
                    }
                }
        return result
    }

    private fun crateFactsWriter(filePath: Path?, stateBefore: DirState<EventDir>, id: ID): Mono<WritableDirState> {
        return this.tasksHandler.putIOTask(idString(id)) { resultPromise ->
            val locked = stateBefore.lock.tryLock()
            if (locked) {

                try {
                    val lastState = this.aggregates.getValue(id)
                    if (lastState == stateBefore) {
                        val createdFile = Files.newBufferedWriter(filePath, StandardOpenOption.CREATE_NEW) //CREATE_NEW would be better
                                as Writer
                        val newStore = stateBefore.copy(
                                dirdata = lastState.dirdata.copy(writeEventStream = Mono.just(Option.of(createdFile)))
                        )
                        val replacement = this.aggregates.replace(id, lastState, newStore)
                        if (replacement) {
                            resultPromise.complete(WritableDirState(createdFile, newStore))
                        } else {
                            TODO("failed replacing state  -should not happen")
                            //createdFile.close() //TODO  what if it fails
                            //ensureEventWriter(id).subscribe({ resultPromise.complete(it) }, { resultPromise.completeExceptionally(it) })
                        }
                    } else {
                        ensureEventWriter(id).subscribe({ resultPromise.complete(it) }, { resultPromise.completeExceptionally(it) })
                    }
                } catch (e: FileAlreadyExistsException) {
                    //Todo how can it happen?
                    println("very interesting - how could it happen? roll ?")
                    e.printStackTrace()
                    ensureEventWriter(id).subscribe({ resultPromise.complete(it) }, { resultPromise.completeExceptionally(it) })
                } finally {
                    stateBefore.lock.unlock()
                }
            } else {
                ensureEventWriter(id).subscribe({ resultPromise.complete(it) }, { resultPromise.completeExceptionally(it) })
            }
        }
    }

    private fun tryWrite(id: ID, fact : FACT, factNode: JsonNode, eventClass: String, trials: Int, currentState: DirState<EventDir>, writer: Writer): Mono<SavedFact<FACT, Unit>> {
        return this.tasksHandler.putIOTask<SavedFact<FACT, Unit>>(idString(id)) { completion ->
            val eventId = currentState.dirdata.nextEventNumber

            val newState = currentState.copy(
                    dirdata = currentState.dirdata.copy(nextEventNumber = eventId + 1)
            )

            val locked = newState.lock.tryLock()
            if (locked) {
                try {
                    val replaced = aggregates.replace(id, currentState, newState)
                    if (replaced) {
                        val capsule = FactCapsule(eventId, clock.instant(), factNode, eventClass)
                        writeData(writer, capsule)
                        completion.complete(SavedFact(eventId, Unit, fact))
                        return@putIOTask
                    } else {
                        //println("small disaster for ${id} @ ${trials}")
                    }
                } catch (e: Exception) {
                    completion.completeExceptionally(e)
                } finally {
                    newState.lock.unlock()
                }
            }
            if (trials > 0) {
                ensureEventWriter(id).flatMap { writable ->
                    tryWrite(id, fact, factNode, eventClass, trials - 1, writable.state, writable.eventWriter)
                }.subscribe({ res -> completion.complete(res) }, { completion.completeExceptionally(it) })
            } else {
                completion.completeExceptionally(RuntimeException("too many trials to save ${id}"))
            }


        }

    }


    private fun writeData(writer: Writer, capsule: FactCapsule) {
        mapper.writeValue(writer, capsule)
        writer.write("\n")
        //writer.flush()//TODO maybe flush later
    }

    override fun shutdown() {
        this.aggregates.values.forEach {
            it.dirdata.writeEventStream.subscribe {
                it.forEach { it.close() }
            }
        }
        super.shutdown()
    }
}

data class EventDir(val nextEventNumber: Long = 0,
                    val writeEventStream: Mono<Option<Writer>> = Mono.just(Option.none()))

internal data class WritableDirState(
        val eventWriter: Writer,
        val state: DirState<EventDir>)

fun eventFileSuffix(number: Long): String {
    val fileNumberSuffix = number.toString().padStart(6, '0')
    return "events_${fileNumberSuffix}"
}


internal class InitialState<EVENT>(
        lastEventMark: LastEventCallback,
        aggrPath: Path,
        mapper: ObjectMapper,
        nextEventId: Long
) : InternalState<EVENT>(lastEventMark, aggrPath, mapper, nextEventId) {
    override fun next(sink: SynchronousSink<SavedFact<EVENT,Unit>>): Option<InternalState<EVENT>> {
        val fileName = eventFileSuffix(nextEventId)
        val readEventsFile = aggrPath.resolve(fileName)
        return if (Files.exists(readEventsFile)) {
            val reader = Files.newBufferedReader(readEventsFile)
            readNextLine(sink, reader)
        } else {
            sink.complete()
            Option.none()
        }
    }
}

internal class FileOpened<EVENT>(
        lastEventMark: LastEventCallback,
        aggrPath: Path,
        mapper: ObjectMapper,
        nextEventId: Long,
        private val reader: BufferedReader
) : InternalState<EVENT>(lastEventMark, aggrPath, mapper, nextEventId) {
    override fun next(sink: SynchronousSink<SavedFact<EVENT,Unit>>): Option<InternalState<EVENT>> =
            readNextLine(sink, reader)

}

typealias LastEventCallback = (Long) -> Unit

internal class EventsReader<EVENT>(
        private val lastEventMark: LastEventCallback,
        private val nextEventId: Long,
        private val aggrPath: Path,
        private val mapper: ObjectMapper) : Consumer<SynchronousSink<SavedFact<EVENT,Unit>>> {

    var state = Option.of(init())

    private fun init(): InternalState<EVENT> = InitialState(lastEventMark, aggrPath, mapper, nextEventId)

    override fun accept(sink: SynchronousSink<SavedFact<EVENT,Unit>>) {
        state = state.flatMap { it.next(sink) }
    }
}

internal sealed class InternalState<EVENT>(
        protected val lastEventMark: LastEventCallback,
        protected val aggrPath: Path,
        private val mapper: ObjectMapper,
        protected val nextEventId: Long
) {
    abstract fun next(sink: SynchronousSink<SavedFact<EVENT,Unit>>): Option<InternalState<EVENT>>

    @Suppress("UNCHECKED_CAST")
    protected fun readNextLine(sink: SynchronousSink<SavedFact<EVENT,Unit>>, reader: BufferedReader): Option<InternalState<EVENT>> {
        var line = reader.readLine()
        return if (line != null) {
            val capsule = mapper.readValue(line, FactCapsule::class.java)
            var eventClass = Class.forName(capsule.eventClass)
            val fact = mapper.treeToValue(capsule.eventContent as TreeNode, eventClass) as EVENT
            val saved = SavedFact( capsule.eventId, Unit, fact)
            sink.next(saved)
            Option.of(FileOpened(lastEventMark, aggrPath, mapper, capsule.eventId + 1, reader))
        } else {
            lastEventMark(nextEventId)

            //sink.complete()
            //Option.none()
            val newState = InitialState<EVENT>(lastEventMark, aggrPath, mapper, nextEventId)
            return newState.next(sink)
        }
    }
}


object BadRegistry {
    val registry = ConcurrentHashMap<String, Boolean>()

    fun register(id: String) {
        registry.put(id, true)
    }

    fun deregister(id: String) {
        registry.remove(id)
    }


    fun debug() {
        registry.forEach { k, _ ->
            println("have ${k}")


        }
    }
}


