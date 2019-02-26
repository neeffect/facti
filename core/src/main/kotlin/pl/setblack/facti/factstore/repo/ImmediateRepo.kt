package pl.setblack.facti.factstore.repo

import io.vavr.Tuple2
import io.vavr.collection.List
import io.vavr.collection.Seq
import pl.setblack.facti.factstore.Command
import pl.setblack.facti.factstore.Query
import pl.setblack.facti.factstore.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux

class ImmediateRepo<ID, STATE, FACT : Any, IDFACT>(private val simpleRepository: Repository<ID, STATE, FACT>) {

    fun <R : Any> execute(id: ID, command: ImmediateCommand<STATE, FACT, R>): Flux<R> =
            simpleRepository.execute(id, CommandWrapper(command))


    fun <R : Any> query(id: ID, q: ImmediateQuery<STATE, FACT, R>): Flux<R> =
            simpleRepository.query(id, QueryWrapper(q))

}

interface ImmediateCommand<STATE, FACT, R> {
    fun apply(stateBefore: STATE): Tuple2<(STATE) -> R, Seq<FACT>>
}

class CommandWrapper<STATE, FACT, R : Any>(val immediate: ImmediateCommand<STATE, FACT, R>) :
        Command<STATE, FACT, R> {
    override fun apply(stateBefore: STATE): Tuple2<(STATE) -> Flux<R>, Flux<FACT>> =
            immediate.apply(stateBefore)
                    .map1 { origFunction -> { state: STATE -> Mono.just(origFunction(state)).toFlux() } }
                    .map2 { Flux.fromIterable(it.asJava()) }

}

class QueryWrapper<STATE, FACT, R : Any>(val immediate: ImmediateQuery<STATE, FACT, R>) :
        Query<STATE, FACT, R> {
    override fun query(stateBefore: STATE): Flux<R> =
            Mono.just(immediate.query(stateBefore)).toFlux()

}

/**
 * This is special type of command that emits no facts.
 *
 * Inheritance on command is only provided for simplicity - some implementations
 * may use this as command. Some may have 'faster' path for queries.
 */
interface ImmediateQuery<STATE, FACT, R> : ImmediateCommand<STATE, FACT, R> {
    fun query(stateBefore: STATE): R

    override fun apply(stateBefore: STATE): Tuple2<(STATE) -> R, Seq<FACT>> =
            Tuple2({ _ -> this.query(stateBefore) }, List.empty())

}

class ImmediateRepoFactory(private  val strategy: RepoCreationStrategy) {

    fun <ID, STATE : Any,FACT : Any, IDFACT>  create(
            creator: (ID) -> STATE,
            factHandler: (STATE, FACT) -> STATE,
            idFromString: (String) -> ID)
               : ImmediateRepo<ID, STATE, FACT, IDFACT> {
        val simpleRepositoryFactory = SimpleRepositoryFactory(creator, factHandler, strategy, idFromString)
        val simpleRepository = simpleRepositoryFactory.create()
        val immediateRepo = ImmediateRepo<ID, STATE, FACT, IDFACT>(simpleRepository)
        return immediateRepo
    }
}