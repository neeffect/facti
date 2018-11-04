package pl.setblack.factstore

interface IOManager {

    fun shutdown()

    fun deleteAll()

    fun restart()
}