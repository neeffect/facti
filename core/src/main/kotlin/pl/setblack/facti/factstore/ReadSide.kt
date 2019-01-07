package pl.setblack.facti.factstore

import pl.setblack.facti.factstore.repo.FactStore
import pl.setblack.facti.factstore.repo.SavedFact
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

interface ReadSide<ID, P> {
    fun recentProjection(id : ID) : Mono<ProjectionCapsule<P>>
}

data class ProjectionCapsule<P>(val projection : P, val lastFactId :  Long)

//TODO I thinkt IDFACT should be removed
interface ReadSideProcessor <ID, FACT : Fact<*>, IDFACT > {
    fun processFact( id: ID, fact : FACT, saved  : SavedFact<IDFACT> ) : Unit
}




class DevNull<ID, FACT : Fact<*>, IDFACT >  :  ReadSideProcessor<ID, FACT, IDFACT> {
    override fun processFact(id: ID, fact: FACT, saved: SavedFact<IDFACT>) = Unit

}

class InMemoryReadSide<ID, P, FACT : Fact<*>>
    ( private val factStore: FactStore<ID, FACT, Unit>,
      private val projector : (ID, P, FACT) -> Mono<P> ) : ReadSide<ID, P>, ReadSideProcessor<ID, FACT, Unit> {


    override fun recentProjection(id: ID): Mono<ProjectionCapsule<P>> {
        TODO("recentProjection not implemented")
    }

    private val projections   = ConcurrentHashMap <ID,  ProjectionCapsule<P>>()

    override fun processFact(id: ID, fact: FACT, saved: SavedFact<Unit>) {
        TODO("processFact not implemented")
    }


}
