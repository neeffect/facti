package pl.setblack.facti.factstore.repo

interface IOManager {

    fun shutdown()

    fun deleteAll()

    fun restart()
}