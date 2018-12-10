package pl.setblack.facti.factstore

import io.vavr.collection.List
import io.vavr.control.Either
import io.vavr.control.Option
import pl.setblack.facti.factstore.repo.SavedFact

interface ReadSide<ID,  FACT : Fact<*>> {

    fun processFact( id: ID, fact : FACT ) : Unit

    //fun recoverLastGlobalFactId() : Option<IDFACT>

    //fun recoverLastAggregateFact(id : ID) : Option<Long>

}

interface ReadSideProcessor <ID, FACT : Fact<*>, IDFACT > {

    fun processFact( id: ID, fact : FACT, saved  : SavedFact<IDFACT> ) : Unit

}

data class ReadSides<ID, FACT : Fact<*>>( private val  readSides : List<ReadSide<ID, FACT>>) {

    fun withReadSide( readSide : ReadSide<ID,FACT> ) = this.copy(readSides= readSides.prepend(readSide))

}


class DevNull<ID, FACT : Fact<*>, IDFACT >  :  ReadSideProcessor<ID, FACT, IDFACT> {
    override fun processFact(id: ID, fact: FACT, saved: SavedFact<IDFACT>) = Unit

}

