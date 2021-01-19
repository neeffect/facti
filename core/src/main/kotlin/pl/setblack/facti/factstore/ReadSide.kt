package pl.setblack.facti.factstore

import io.vavr.control.Option
import pl.setblack.facti.factstore.repo.FactStore
import pl.setblack.facti.factstore.repo.SavedFact
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

interface ReadSide<ID, P> {
    fun recentProjection(id: ID): Mono<ProjectionCapsule<P>>
}

data class ProjectionCapsule<P>(val projection: P, val lastFactId: Long)

//TODO I thinkt IDFACT should be removed
interface ReadSideProcessor<ID, FACT, IDFACT> {
    fun processFact(id: ID, saved: SavedFact<FACT, IDFACT>): Mono<Unit>
}

class DevNull<ID, FACT, IDFACT> : ReadSideProcessor<ID, FACT, IDFACT> {
    override fun processFact(id: ID, saved: SavedFact<FACT, IDFACT>) = Mono.just(Unit)

}

class InMemoryReadSide<ID, P, FACT :Any>
(private val factStore: FactStore<ID, FACT, Unit>,
 private val projector: (ID, Option<P>, FACT) -> P) : ReadSide<ID, P>, ReadSideProcessor<ID, FACT, Unit> {

    private val projections = ConcurrentHashMap<ID, ProjectionCapsule<P>>()

    override fun recentProjection(id: ID): Mono<ProjectionCapsule<P>> {
        val stored = Option.of(projections[id])
        return stored.map {
            Mono.just(it!!)
        }.getOrElse {
            val restored = factStore.loadFacts(id, 0)
            restored.map {
                processFact(id, it)
            }.last().map {
                projections[id]
            }
        }
    }

    override fun processFact(id: ID, saved: SavedFact<FACT, Unit>) =
        projections.compute(id) { _, oldVal ->
            val newValue = projector(id, Option.of(oldVal).map { it?.projection }, saved.fact)
            ProjectionCapsule(newValue, saved.thisFactIndex)
        }.let {
            Mono.just(Unit)
        }



}
