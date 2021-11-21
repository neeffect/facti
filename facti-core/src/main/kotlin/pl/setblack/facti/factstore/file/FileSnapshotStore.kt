package pl.setblack.facti.factstore.file

import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.JsonNode
import io.vavr.Tuple2
import io.vavr.collection.Stream
import io.vavr.control.Option
import pl.setblack.facti.factstore.repo.SavedState
import pl.setblack.facti.factstore.repo.SnapshotData
import pl.setblack.facti.factstore.repo.SnapshotStore
import pl.setblack.facti.factstore.util.TasksHandler
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock

class FileSnapshotStore<ID, STATE : Any>(
    basePath: Path,
    clock: Clock,
    tasksHandler: TasksHandler) :
    DirBasedStore<ID, SnapshotDir>(basePath, clock, tasksHandler), SnapshotStore<ID, STATE> {

    private val snapshotPrefix = "snapshot_"

    private val initialState = SnapshotDir()

    override fun restore(id: ID, supplier: (ID) -> Mono<STATE>): Mono<SnapshotData<STATE>> {

        return readLastSnapshot(id).flatMap { maybeSnapshot ->
            maybeSnapshot.map { Mono.just(it) }
                .getOrElse {
                    supplier(id).map { SnapshotData(it) }
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
                SnapshotData(state, savedSnapshot.nextEventId) as SnapshotData<STATE>
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
}

data class SnapshotDir(val nextSnapshotNumber: Long = 0)

internal data class Snapshot<STATE : Any>(
    val state: STATE,
    val stateClass: String = state::class.java.name
)

internal data class SavedSnapshot(
    val nextEventId: Long,
    val state: JsonNode,
    val stateClass: String = state::class.java.name

)
