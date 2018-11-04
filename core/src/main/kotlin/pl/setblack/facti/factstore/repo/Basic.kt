package pl.setblack.facti.factstore.repo

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Definition for every factstore.
 */
interface FactStore<ID, FACT> {
    fun persist(id: ID, ev: FACT): Mono<SavedFact>

    fun loadFacts(id: ID, offset: Long): Flux<FACT>

    fun roll(id: ID): Mono<Long>
}

/**
 * Definition for every snapshotstore.
 */
interface SnapshotStore<ID, STATE> {

    fun restore(id: ID, supplier: (ID) -> Mono<STATE>): Mono<SnapshotData<STATE>>

    fun snapshot(id: ID, state: SnapshotData<STATE>): Mono<SavedState<STATE>>
}

data class SavedFact(val thisFactIndex: Long)

data class SavedState<STATE>(val snapshotIndex: Long, val state: STATE)

data class SnapshotData<STATE>(val state: STATE, val nextFactSeq: Long = 0)