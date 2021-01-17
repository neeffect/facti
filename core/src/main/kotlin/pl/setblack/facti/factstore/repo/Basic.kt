package pl.setblack.facti.factstore.repo

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Main interface for persisting facts.
 *
 *
 * @param ID  type of identificator of objects
 * @param FACT type of facts
 * @param IDFACT  (todo) - save fact ID
 *
 */
interface FactStore<ID, FACT: Any, IDFACT> {
    fun persist(id: ID, fact: FACT): Mono<SavedFact<FACT, IDFACT>>

    fun loadFacts(id: ID, offset: Long): Flux<SavedFact<FACT, IDFACT>>

    fun roll(id: ID): Mono<Long>

    fun loadAll(lastFact : IDFACT) : Flux<LoadedFact<ID, FACT>>
}

/**
 * Definition for every snapshotstore.
 */
interface SnapshotStore<ID, STATE> {

    fun restore(id: ID, supplier: (ID) -> Mono<STATE>): Mono<SnapshotData<STATE>>

    fun snapshot(id: ID, state: SnapshotData<STATE>): Mono<SavedState<STATE>>
}

data class SavedFact<FACT, IDFACT>(val thisFactIndex: Long, val idFact :IDFACT, val fact : FACT)

data class SavedState<STATE>(val snapshotIndex: Long, val state: STATE)

data class SnapshotData<STATE>(val state: STATE, val nextFactSeq: Long = 0)

data class LoadedFact<ID,  FACT> (val id: ID,  val fact : FACT)
