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
interface Repository<ID, STATE, FACT : Fact<STATE>> {

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

    fun snapshot(id: ID) : Mono<STATE>
}


interface Command<STATE, FACT : Fact<STATE>, R> {
    fun apply(stateBefore: STATE): Tuple2<(STATE) -> Flux<R>, Flux<FACT>>
}

interface Query<STATE, FACT : Fact<STATE>, R> : Command<STATE, FACT, R> {
    fun query(stateBefore: STATE): Flux<R>

    override fun apply(stateBefore: STATE): Tuple2<(STATE) -> Flux<R>, Flux<FACT>> =
            Tuple2({ _ -> this.query(stateBefore) }, Flux.empty())

}

interface Fact<S> {
    fun apply(state: S): S
}