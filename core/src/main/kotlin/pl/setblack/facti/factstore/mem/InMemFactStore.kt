package pl.setblack.facti.factstore.mem

import io.vavr.collection.List
import io.vavr.control.Option

import pl.setblack.facti.factstore.repo.FactStore
import pl.setblack.facti.factstore.repo.LoadedFact
import pl.setblack.facti.factstore.repo.SavedFact
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

//TODO test  separately
class InMemFactStore<ID, FACT: Any> : FactStore<ID, FACT, Unit> {
    private val allFacts  = ConcurrentHashMap<ID, Facts<FACT>>()

    override fun persist(id: ID, fact: FACT): Mono<SavedFact<FACT, Unit>> {
        val newFacts  = allFacts.compute( id) { id, oldFacts->
            val nonEmptyFacts = Option.of(oldFacts).getOrElse(Facts())
            nonEmptyFacts?.addOne( fact)
        }!!
        return Mono.just(newFacts.lastSaved())
    }

    override fun loadFacts(id: ID, offset: Long): Flux<SavedFact<FACT, Unit>> {
        val facts = allFacts.getOrDefault(id, Facts())
        return Flux.fromIterable(facts.facts.subSequence(offset.toInt()))
    }

    override fun roll(id: ID): Mono<Long> =
       Mono.just(allFacts.getOrDefault(id, Facts()).facts.size().toLong())


    override fun loadAll(lastFact: Unit): Flux<LoadedFact<ID, FACT>> {
        TODO("loadAll not implemented")
    }
}

internal data class Facts<FACT > (val facts : List<SavedFact<FACT, Unit>> = List.empty()) {
    fun addOne(fact : FACT) = this.copy( facts = this.facts.append(SavedFact(facts.size().toLong(), Unit, fact)))
    fun lastSaved(): SavedFact<FACT, Unit>  = this.facts.last()

}
