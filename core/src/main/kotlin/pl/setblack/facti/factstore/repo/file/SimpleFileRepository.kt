package pl.setblack.facti.factstore.repo.file

import pl.setblack.facti.factstore.Fact
import pl.setblack.facti.factstore.Repository
import pl.setblack.facti.factstore.file.FileFactStore
import pl.setblack.facti.factstore.file.FileSnapshotStore
import pl.setblack.facti.factstore.repo.SimpleRepository
import pl.setblack.facti.factstore.util.SimpleTaskHandler
import java.nio.file.Path
import java.time.Clock

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