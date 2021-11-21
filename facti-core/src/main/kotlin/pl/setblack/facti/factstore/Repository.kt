package pl.setblack.facti.factstore

import io.vavr.Tuple2
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 *  Basic repository interface.
 *
 * @param ID - type of key.  Example: Long, String, UUID
 * @param STATE - type of state Object. UserData, Product... Should be immutable!
 * @param FACT - type of  facts on state. Typically implemented as sealed class
 */
interface Repository<ID, STATE, FACT> {

    /**
     * Execute command on the aggregate identified by id,
     *
     * @return stream of results
     */
    fun <R> execute(id: ID, command: Command<STATE, FACT, R>): Flux<R>

    /**
     * Execute read only command on the aggregate
     *
     * @return stream of results
     */
    fun <R> query(id: ID, q: Query<STATE, FACT, R>): Flux<R>

    /**
     * Make a snapshot of an aggregate.
     * This may save an aggregate in a storage.
     *
     * It is best not to call this method too often. Most of repositories should
     * make snapshots according to some strategy.
     *
     * @return state of a saved aggregate
     */
    fun snapshot(id: ID): Mono<STATE>


}


/**
 * Command is an function that for a given aggregate state returns a stream of facts, and a function
 * that takes a final state (after facts are applied) and return some result.
 *
 * Notice: it is not guaranteed that:  facts  foldl stateBefore== final state,
 * there may be some facts  from other systems and other commands that are applied in the meantime.
 *
 */
interface Command<STATE, FACT, R> {
    fun apply(stateBefore: STATE): Tuple2<(STATE) -> Flux<R>, Flux<FACT>>
}

/**
 * This is special type of command that emits no facts.
 *
 * Inheritance on command is only provided for simplicity - some implementations
 * may use this as command. Some may have 'faster' path for queries.
 */
interface Query<STATE, FACT, R> : Command<STATE, FACT, R> {
    fun query(stateBefore: STATE): Flux<R>

    override fun apply(stateBefore: STATE): Tuple2<(STATE) -> Flux<R>, Flux<FACT>> =
        Tuple2({ _ -> this.query(stateBefore) }, Flux.empty())

}

/**
 * Fact is a transformation of an aggregate.
 *
 * From initial  state to a new state.
 * Facts should never ever change/ mutate existing state.
 */
/*interface Fact<S> {
    fun apply(state: S): S
}*/

