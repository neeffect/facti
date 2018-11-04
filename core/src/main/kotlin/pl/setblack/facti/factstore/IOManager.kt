package pl.setblack.facti.factstore

interface IOManager {

    fun shutdown()

    fun deleteAll()

    fun restart()
}