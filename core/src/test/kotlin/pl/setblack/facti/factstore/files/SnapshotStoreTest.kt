package pl.setblack.facti.factstore.files

import pl.setblack.facti.factstore.file.FileSnapshotStore

import io.kotlintest.shouldBe
import io.kotlintest.specs.DescribeSpec
import pl.setblack.facti.factstore.bank.simplified.SimpleAccount
import pl.setblack.facti.factstore.repo.SavedState
import pl.setblack.facti.factstore.repo.SnapshotData
import pl.setblack.facti.factstore.repo.SnapshotStore
import pl.setblack.facti.factstore.util.SimpleTaskHandler
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Clock
import java.time.LocalDateTime
import java.util.*


const val mainAccountId = "irreg1"
const val otherAccountId = "ot\nhe\n\rr"

internal class SnapshotStoreTest : DescribeSpec({

    describe("for an event store in a temp folder ") {
        val timeZone = TimeZone.getTimeZone("GMT+0:00")
        val initialTime = LocalDateTime.parse("2018-10-01T10:00")
        val clock = Clock.fixed(initialTime.atZone(timeZone.toZoneId()).toInstant(), timeZone.toZoneId())
        val tmpDir = Files.createTempDirectory("facti-snaphot-test")
        val tasksHandler = SimpleTaskHandler()
        val snapshotStore = FileSnapshotStore<String, SimpleAccount>(tmpDir, clock, tasksHandler)

        val accountInitializer = { id: String -> Mono.just(SimpleAccount(id)) }

        it("should create initial state") {
            snapshotStore.deleteAll()
            val initialState = snapshotStore.restore(mainAccountId, accountInitializer)
            StepVerifier.create(initialState).assertNext { simpleAccountData ->
                simpleAccountData.state.id shouldBe (mainAccountId)
            }.verifyComplete()
        }

        context("restore") {
            snapshotStore.deleteAll()

            val restored = snapshotStore.restore(mainAccountId, supplier = accountInitializer)
            it("should restore initial snapshot") {
                val initialState = restored
                StepVerifier.create(initialState).assertNext {
                    it.state.initial shouldBe (BigDecimal.ZERO)
                }.verifyComplete()
            }
        }


        context("snapshot") {
            snapshotStore.deleteAll()
            val account = SimpleAccount(mainAccountId, BigDecimal.valueOf(42))
            val snapshot = snapshotStore.snapshot(mainAccountId, SnapshotData(account))
            it("snapshot should be made") {
                StepVerifier.create(snapshot)
                        .expectNextCount(1)
                        .verifyComplete()
            }

            it("should restore state") {
                val restoredState = snapshotStore.restore(mainAccountId, accountInitializer)
                StepVerifier.create(restoredState).assertNext {
                    it.state.initial shouldBe (BigDecimal.valueOf(42))
                }.verifyComplete()
            }
        }

        context("multiple snapshot restore") {
            snapshotStore.deleteAll()
            val restoredState = prepareTwoSnaphotsCase(snapshotStore)
            it("will read first account") {
                val restored1 = restoredState.flatMap { snapshotStore.restore(mainAccountId, accountInitializer) }
                StepVerifier.create(restored1)
                        .assertNext {
                            it.state.initial.shouldBe(BigDecimal.valueOf(49))
                        }.verifyComplete()
            }
            it("will read other account") {
                val restored2 = restoredState.flatMap { snapshotStore.restore(otherAccountId, accountInitializer) }
                StepVerifier.create(restored2)
                        .assertNext {
                            it.state.initial.shouldBe(BigDecimal.valueOf(62))
                        }.verifyComplete()
            }

        }
    }
    //TODO test eventId storing
    //TODO fix namings of folders in file
})


internal fun prepareTwoSnaphotsCase(snapshotStore: SnapshotStore<String, SimpleAccount>): Mono<SavedState<SimpleAccount>> {
    val account1 = SimpleAccount(mainAccountId, BigDecimal.valueOf(42))
    val account2 = SimpleAccount(otherAccountId, BigDecimal.valueOf(62))
    val snapshot1 = snapshotStore.snapshot(mainAccountId, SnapshotData(account1))
    val snapshot2 = snapshot1.flatMap {
        snapshotStore.snapshot(otherAccountId, SnapshotData(account2))
    }
    val account1a = SimpleAccount(mainAccountId, BigDecimal.valueOf(49))
    val snapshot1a = snapshot2.flatMap {
        snapshotStore.snapshot(mainAccountId, SnapshotData(account1a))
    }
    return snapshot1a
}