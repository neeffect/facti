package pl.setblack.facti.factstore.file

import pl.setblack.facti.factstore.repo.FactStore
import pl.setblack.facti.factstore.repo.RepoCreationStrategy
import pl.setblack.facti.factstore.repo.SnapshotStore
import pl.setblack.facti.factstore.util.SimpleTaskHandler
import java.nio.file.Path
import java.time.Clock

class FileRepoCreator(
    private val basePath: Path,
    private val clock: Clock


) : RepoCreationStrategy {
    private val tasksHandler = SimpleTaskHandler()
    override fun <ID, STATE : Any> createSnapshotStore(): SnapshotStore<ID, STATE> =
        FileSnapshotStore<ID, STATE>(basePath, clock, tasksHandler)

    override fun <ID, FACT : Any> createFactStore(idFromString: (String) -> ID): FactStore<ID, FACT, Unit> =
        FileFactStore<ID, FACT>(
            basePath,
            clock,
            tasksHandler,
            idFromString
        )
}
