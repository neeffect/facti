package pl.setblack.facti.factstore.file

import pl.setblack.facti.factstore.SavedState
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.JsonNode
import io.vavr.Tuple2
import io.vavr.collection.Stream
import io.vavr.control.Option
import pl.setblack.facti.factstore.SnapshotData
import pl.setblack.facti.factstore.SnapshotStore
import pl.setblack.facti.factstore.util.TasksHandler
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock

class FileSnapshotStore<ID, STATE : Any>(
        basePath: Path,
        clock: Clock,
        tasksHandler: TasksHandler) :
        DirBasedStore<ID, SnapshotDir>(basePath, clock, tasksHandler), SnapshotStore<ID, STATE>  {

    private val snapshotPrefix = "snapshot_"

    private val initialState = SnapshotDir()

    override fun restore(id: ID, supplier: (ID) -> Mono<STATE>): Mono<SnapshotData<STATE>> {

        return readLastSnapshot(id).flatMap { maybeSnapshot ->
            maybeSnapshot.map { Mono.just(it) }
                    .getOrElse {
                        supplier(id).map { SnapshotData( it) }
                    }
        }
    }

    override fun snapshot(id: ID, state: SnapshotData<STATE>): Mono<SavedState<STATE>> =
            findAggregateStore(id, initialState).aggregatePath.map { aggregatePath ->
                trySnapshot(id, state, aggregatePath)
            }

    private fun trySnapshot(id: ID, state: SnapshotData<STATE>, aggregatePath: Path): SavedState<STATE> {
        val oldStore = findAggregateStore(id, initialState)//This repetition is also scary
        val snapshotNumber = oldStore.dirdata.nextSnapshotNumber
        val snapshotName = snapshotFileName(snapshotNumber)
        val snapshot = SavedSnapshot(state.nextFactSeq, mapper.valueToTree(state.state), state.state.javaClass.name)
        val newStore = oldStore.copy(dirdata = oldStore.dirdata.copy(nextSnapshotNumber = oldStore.dirdata.nextSnapshotNumber + 1))
        val replaced = this.aggregates.replace(id, oldStore, newStore)
        return if (replaced) {
            val writer = Files.newBufferedWriter(aggregatePath.resolve(snapshotName))
            mapper.writeValue(writer, snapshot)
            writer.close()
            SavedState(snapshotNumber, state.state)
        } else {
            return trySnapshot(id, state, aggregatePath)
        }
    }

    private fun findLastSnapshotFile(store: DirState<SnapshotDir>, aggregatePath: Path): Option<Tuple2<DirState<SnapshotDir>, Path>> =
            if (store.dirdata.nextSnapshotNumber > 0) {
                val snapshotNumber = store.dirdata.nextSnapshotNumber - 1
                Option.of(Tuple2(store, aggregatePath.resolve(snapshotFileName(snapshotNumber))))
            } else {
                findLastSnapshotOnDisk(aggregatePath).map {
                    Tuple2(store.copy(dirdata = store.dirdata.copy(nextSnapshotNumber = it._1 + 1)), it._2)
                }
            }

    private fun findLastSnapshotOnDisk(aggregatePath: Path): Option<Tuple2<Long, Path>> {
        val snapshotsStream = Files.newDirectoryStream(aggregatePath, "${snapshotPrefix}*")
        val recentSnapshot = Stream.ofAll(snapshotsStream).maxBy { a, b -> a.fileName.toString().substring(snapshotPrefix.length).compareTo(b.fileName.toString().substring(snapshotPrefix.length)) }
        return recentSnapshot.map {
            Tuple2(it.fileName.toString().substring(snapshotPrefix.length).toLong(), it)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readLastSnapshot(id: ID): Mono<Option<SnapshotData<STATE>>> =
            findAggregateStore(id, initialState).aggregatePath.map { aggregatePath ->
                val store = findAggregateStore(id, initialState) //why this repetition ?
                var storedSnapshot = findLastSnapshotFile(store, aggregatePath)
                storedSnapshot.map { existingFile ->
                    val snapshotFile = existingFile._2
                    val reader = Files.newBufferedReader(snapshotFile)
                    val savedSnapshot = mapper.readValue(reader, SavedSnapshot::class.java)
                    reader.close()
                    val stateClass = Class.forName(savedSnapshot.stateClass)
                    val state = mapper.treeToValue(savedSnapshot.state as TreeNode, stateClass)
                    SnapshotData(state, savedSnapshot.nextEventId)  as SnapshotData<STATE>
                    /*
                    TODO it was for events only
                    val newStore = store

                    if (aggregates.replace(id, store, newStore)) {
                        Snapshot(savedSnapshot.nextEventId, state) as Snapshot<STATE>//TODO surely adaptation of DirState is missing (replace)
                    } else {
                        TODO("not implemented")
                    }*/
                }
            }



    private fun snapshotFileName(snapshotNumber: Long): String {
        val fileNumberSuffix = snapshotNumber.toString().padStart(6, '0')
        val snapshotName = "snapshot_" + fileNumberSuffix
        return snapshotName
    }





    /*private fun ensureEventWriter(id: ID): Mono<WritableDirState> {

        val result = findAggregateStore(id).aggregatePath
                .flatMap { aggregatePath ->
                    //renewing store
                    findAggregateStore(id).writeEventStream.map { writer ->
                        val store = findAggregateStore(id)//TODO renewed
                        writer
                                .map {
                                    WritableDirState(it, store)
                                }
                                .getOrElse {
                                    val filePath = aggregatePath.resolve(eventFileSuffix(store.currentEvent))
                                    try {
                                        val createdFile = Files.newBufferedWriter(filePath, StandardOpenOption.CREATE_NEW)
                                                as Writer
                                        val newStore = store.copy(
                                                writeEventStream = Mono.just(Option.of(createdFile))
                                        )
                                        val replacement = this.aggregates.replace(id, store, newStore)
                                        if (replacement) {
                                            WritableDirState(createdFile, newStore)
                                        } else {
                                            TODO("failed replacing state")
                                        }
                                    } catch (e: IOException) {
                                        throw RuntimeException(e)
                                    }

                                }
                    }
                }
        return result
    }*/

    /*internal fun updateLastEventId(id: ID, eventId: Long) {
        this.aggregates.computeIfPresent(id) { key, oldStore -> oldStore.copy(currentEvent = eventId) }
    }*/





    // fact store part

    /*
    private fun tryWrite(id: ID, eventString: String, eventClass: String, trials: Int, writer: Writer): SavedState {
        val currentState = this.aggregates.getValue(id)
        val eventId = currentState.currentEvent

        val newState = currentState.copy(
                currentEvent = eventId + 1
        )
        val capsule = FactCapsule(eventId, clock.instant(), eventString, eventClass)

        val replaced = aggregates.replace(id, currentState, newState)
        if (replaced) {
            if (newState.lock.tryLock()) {
                try {
                    mapper.writeValue(writer, capsule)
                    writer.write("\n")
                    writer.flush()
                } finally {
                    newState.lock.unlock()
                }
            }
            return SavedState(eventId)
        } else {
            if (trials > 0) {
                return tryWrite(id, eventString, eventClass, trials - 1, writer)
            } else {
                TODO("too many failures in writing")
            }
        }
    }*/
    /*override fun restore(id: ID, supplier: (ID) -> Mono<STATE>): Mono<STATE> {

        return readLastSnapshot(id).flatMap { maybeSnapshot ->
            maybeSnapshot.map { Mono.just(it) }
                    .getOrElse {
                        supplier(id).map { Snapshot(1, it) }
                    }
                    .map { snapshot ->
                        val events = findFolder(id).toFlux().flatMap { aggregatePath ->
                            val lastEventCallback = { eventId: Long ->
                                this.aggregates.computeIfPresent(id) { key, oldDirState ->
                                    oldDirState.copy(
                                            currentEvent = eventId,
                                            writeEventStream = Mono.just(Option.none())
                                    )
                                }
                                Unit
                            }
                            val restoredEvents = Flux.generate<EVENT>(
                                    EventsReader(lastEventCallback, snapshot.nextEventId, aggregatePath, mapper))
                            restoredEvents
                        }
                        LoadingRoot(Mono.just(snapshot.state), events)
                    }
        }
    }*/
    /*private fun readLastSnapshot(id: ID): Mono<Option<Snapshot<STATE>>> =
            findAggregateStore(id).aggregatePath.map { aggregatePath ->
                val store = findAggregateStore(id)
                var storedSnapshot = findLastSnapshotFile(store, aggregatePath)
                storedSnapshot.map { existingFile ->
                    val snapshotFile = existingFile._2
                    val reader = Files.newBufferedReader(snapshotFile)
                    val savedSnapshot = mapper.readValue(reader, SavedSnapshot::class.java)
                    reader.close()

                    val stateClass = Class.forName(savedSnapshot.stateClass)
                    val state = mapper.treeToValue(savedSnapshot.state as TreeNode, stateClass)
                    val newStore = store.copy(currentEvent = savedSnapshot.nextEventId)
                    if (aggregates.replace(id, store, newStore)) {
                        Snapshot(savedSnapshot.nextEventId, state) as Snapshot<STATE>//TODO surely adaptation of DirState is missing (replace)
                    } else {
                        TODO("not implemented")
                    }
                }
            }*/
}

data class SnapshotDir( val nextSnapshotNumber: Long = 0)

internal data class Snapshot<STATE : Any>(
        val state: STATE,
        val stateClass: String = state::class.java.name
)

internal data class SavedSnapshot(
        val nextEventId : Long,
        val state: JsonNode,
        val stateClass: String = state::class.java.name

)
