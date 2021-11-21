package pl.setblack.facti.factstore.mem

import pl.setblack.facti.factstore.repo.SavedState
import pl.setblack.facti.factstore.repo.SnapshotData
import pl.setblack.facti.factstore.repo.SnapshotStore
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

//TODO not tested
class InMemorySnapshotStore<ID, STATE> : SnapshotStore<ID, STATE> {
    private val snapshots: MutableMap<ID, SnapshotData<STATE>> = ConcurrentHashMap()

    override fun restore(id: ID, supplier: (ID) -> Mono<STATE>): Mono<SnapshotData<STATE>> =
        this.snapshots.get(id)?.let {
            Mono.just(it)
        } ?: supplier(id).map {
            SnapshotData<STATE>(it, 0)
        }

    override fun snapshot(id: ID, state: SnapshotData<STATE>): Mono<SavedState<STATE>> {
        this.snapshots.put(id, state)
        return Mono.just(SavedState(0, state.state))
    }
}
