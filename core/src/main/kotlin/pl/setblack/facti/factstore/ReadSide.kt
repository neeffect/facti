package pl.setblack.facti.factstore

import io.vavr.collection.List

interface ReadSide<ID,  FACT : Fact<*> > {

    fun processFact( id: ID, fact : FACT ) : Unit



}


interface ReadSideProcessor <ID, FACT : Fact<*> > {
    fun processFact( id: ID, fact : FACT ) : Unit
}


data class ReadSides<ID, FACT : Fact<*>>( private val  readSides : List<ReadSide<ID, FACT>>) {

    fun withReadSide( readSide : ReadSide<ID,FACT> ) = this.copy(readSides= readSides.prepend(readSide))

}