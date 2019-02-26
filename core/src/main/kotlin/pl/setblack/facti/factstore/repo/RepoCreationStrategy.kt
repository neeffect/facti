package pl.setblack.facti.factstore.repo

import pl.setblack.facti.factstore.mem.InMemoryFactStore
import pl.setblack.facti.factstore.mem.InMemorySnapshotStore

interface RepoCreationStrategy {
    fun <ID, STATE : Any>  createSnapshotStore() : SnapshotStore<ID, STATE>

    fun <ID, FACT : Any> createFactStore(idFromString: (String) -> ID): FactStore<ID, FACT, Unit>
}

class InMemCreator : RepoCreationStrategy {
    override fun <ID, STATE : Any> createSnapshotStore(): SnapshotStore<ID, STATE> =
        InMemorySnapshotStore<ID, STATE>()

    override fun <ID, FACT : Any> createFactStore(idFromString: (String) -> ID): FactStore<ID, FACT, Unit>  =
        InMemoryFactStore<ID, FACT>()
}