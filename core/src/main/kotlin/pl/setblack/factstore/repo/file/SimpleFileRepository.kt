package pl.setblack.factstore.repo.file

import pl.setblack.factstore.Fact
import pl.setblack.factstore.Repository
import pl.setblack.factstore.file.FileFactStore
import pl.setblack.factstore.file.FileSnapshotStore
import pl.setblack.factstore.repo.simple.AggregateSaved
import pl.setblack.factstore.repo.simple.SimpleRepository
import pl.setblack.factstore.util.SimpleTaskHandler
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

/**
 * TODO - use clock by denek
 */
class  SimpleFileRepositoryFactory<ID, STATE: Any, FACT : Fact<STATE>>(
        private val creator: (ID) -> STATE,
        val basePath : Path,
        val clock : Clock) {

    fun  create( ) : Repository<ID, STATE, FACT> {
        val tasksHandler = SimpleTaskHandler(3)

        val factStore = FileFactStore<ID, FACT>(basePath, clock, tasksHandler )
        val snapshotStore = FileSnapshotStore<ID, STATE>(basePath, clock, tasksHandler)

        return SimpleRepository(
                creator,
                factStore,
                snapshotStore,
                tasksHandler
        )

    }
}