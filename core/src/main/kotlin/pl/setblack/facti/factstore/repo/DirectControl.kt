package pl.setblack.facti.factstore.repo

/**
 * Special marker interface that may be supported by stores.
 *
 * Low level IO / control operations. Useful for testing.
 */
interface DirectControl {

    fun shutdown()

    fun deleteAll()

    fun restart()
}
