package pl.setblack.facti.factstore

import io.vavr.Tuple2
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


interface Repository<ID, STATE, FACT : Fact<STATE>> {

    fun <R> execute(id: ID, command: Command<STATE, FACT, R>): Flux<R>

    fun <R> query(id: ID, q: Command<STATE, FACT, R>): Flux<R>

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