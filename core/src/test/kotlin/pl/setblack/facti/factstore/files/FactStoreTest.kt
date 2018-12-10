package pl.setblack.facti.factstore.files


import io.kotlintest.shouldBe
import io.kotlintest.specs.DescribeSpec
import io.vavr.collection.Array
import io.vavr.collection.Map
import io.vavr.collection.HashMap
import pl.setblack.facti.factstore.Fact
import pl.setblack.facti.factstore.ReadSide
import pl.setblack.facti.factstore.ReadSideProcessor
import pl.setblack.facti.factstore.bank.simplified.MoneyTransfered
import pl.setblack.facti.factstore.bank.simplified.AccountFact
import pl.setblack.facti.factstore.bank.simplified.InitialTransfer
import pl.setblack.facti.factstore.bank.simplified.identity
import pl.setblack.facti.factstore.file.FileFactStore
import pl.setblack.facti.factstore.repo.SavedFact
import pl.setblack.facti.factstore.util.SimpleTaskHandler
import reactor.core.publisher.Flux
import reactor.core.publisher.toFlux
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Clock
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicReference



internal class FactStoreTest : DescribeSpec({

    describe("for an fact store in a temp folder ") {
        val timeZone = TimeZone.getTimeZone("GMT+0:00")
        val initialTime = LocalDateTime.parse("2018-10-01T10:00")
        val clock = Clock.fixed(initialTime.atZone(timeZone.toZoneId()).toInstant(), timeZone.toZoneId())
        val tmpDir = Files.createTempDirectory("facti-filestore-test")
        val tasksHandler = SimpleTaskHandler()
        val factStore = FileFactStore<String, AccountFact>(tmpDir, clock, tasksHandler, idFromString = identity)

        context("persist event") {
            factStore.deleteAll()
            val persisted = factStore.persist(mainAccountId, MoneyTransfered(BigDecimal.ONE, otherAccountId))

            it("should be saved") {
                StepVerifier.create(persisted).assertNext {
                    it.thisFactIndex > 0
                }.verifyComplete()
            }
        }


        context("restore") {
            factStore.deleteAll()
            val events = Array.range(0, 3)
                    .map { MoneyTransfered(BigDecimal.valueOf(it.toLong()), otherAccountId) }
                    .map { factStore.persist(mainAccountId, it) }
                    .map { it.toFlux() }
            val persisted = Flux.concat(events)
            persisted.blockLast()
            factStore.restart()
            val restoredFacts = factStore.loadFacts(mainAccountId, 0)
            it("should restore all  events") {
                StepVerifier.create(restoredFacts).expectNextCount(3).verifyComplete()
            }
            it("should have correct last  event") {
                StepVerifier.create(restoredFacts.last()).assertNext {
                    (it as MoneyTransfered).amountDiff shouldBe (BigDecimal.valueOf(2))
                }.verifyComplete()

            }
        }
        context("persist several events") {

            val events = Array.range(0, 10)
                    .map { MoneyTransfered(BigDecimal.valueOf(it.toLong()), otherAccountId) }
                    .map { factStore.persist(mainAccountId, it) }
                    .map { it.toFlux() }

            val persisted = Flux.concat(events)

            it("should process 10 events") {
                factStore.deleteAll()
                StepVerifier.create(persisted)
                        .expectNextCount(10)
                        .verifyComplete()
            }
            it("should  have 10 trasactions at the end ") {
                factStore.deleteAll()
                StepVerifier.create(persisted.last())
                        .assertNext { it.thisFactIndex shouldBe (9) }
                        .verifyComplete()
            }
        }

        context("events after roll") {
            factStore.deleteAll()
            val events1 = Array.range(0, 10)
                    .map { MoneyTransfered(BigDecimal.valueOf(it.toLong()), otherAccountId) }
                    .map { factStore.persist(mainAccountId, it) }
                    .map { it.toFlux() }
            val events2 = Array.range(0, 2)
                    .map { MoneyTransfered(BigDecimal.valueOf(it.toLong()), otherAccountId) }
                    .map { factStore.persist(mainAccountId, it) }
                    .map { it.toFlux() }

            val fullStory = Flux.concat(events1).collectList().flatMap {
                factStore.roll(mainAccountId).flatMap {
                    Flux.concat(events2).collectList()
                            .flatMap { factStore.roll(mainAccountId) }

                }
            }
            fullStory.block()

            it("3 stored events are loaded") {
                factStore.shutdown()
                val restored = factStore.loadFacts(mainAccountId, 10)
                StepVerifier.create(restored).expectNextCount(2).verifyComplete()
            }
            it("all events are restored") {
                factStore.shutdown()
                val restored = factStore.loadFacts(mainAccountId, 0)
                StepVerifier.create(restored).expectNextCount(12).verifyComplete()
            }

            it("should read all for the readsides") {
                factStore.shutdown()
                val restored = factStore.loadFacts(mainAccountId, 0)
                restored.blockLast()
                var loaded = factStore.loadAll(Unit)
                StepVerifier.create(loaded).expectNextCount(12).verifyComplete()
            }

        }



    }

    describe("for a fact store with a read side ") {
        val timeZone = TimeZone.getTimeZone("GMT+0:00")
        val initialTime = LocalDateTime.parse("2018-10-01T10:00")
        val clock = Clock.fixed(initialTime.atZone(timeZone.toZoneId()).toInstant(), timeZone.toZoneId())
        val tmpDir = Files.createTempDirectory("facti-filestore-test")
        val tasksHandler = SimpleTaskHandler()
        val readSide = ObjReadSide(::processBankFacts, AllAccounts() )
        val factStore = FileFactStore<String, AccountFact>(tmpDir, clock, tasksHandler, readSide, idFromString = identity)

        context("read side") {
            factStore.deleteAll()

            val events1 = Array.range(0, 10)
                    .map { MoneyTransfered(BigDecimal.valueOf(it.toLong()), otherAccountId) }
                    .map { factStore.persist(mainAccountId, it) }
                    .map { it.toFlux() }
            val persisted = Flux.concat(events1)
            it("should process all read side facts for a single aggregate") {
                persisted.blockLast()
                readSide.getObject().transfers shouldBe 10
            }

            it("should restore read side if it is deleted") {
                TODO("maybe move this test to ReadSideProcessor")

            }
        }

    }
})

data class AllAccounts(val accounts: Map<String, AccountSum> = HashMap.empty(), val transfers: Long = 0) {
    fun init( id: String, start : BigDecimal) = this.copy(
            accounts = accounts.put(id, accounts.getOrElse(id, AccountSum()).init(start))
    )
    fun add ( id :String, change : BigDecimal) =this.copy(
            accounts = accounts.put(id, accounts.getOrElse(id, AccountSum()).add(change)),
            transfers =  transfers + 1
    )

}
data class AccountSum(val money: BigDecimal = 0.toBigDecimal(), val transfers: Long = 0) {
    fun init(start : BigDecimal)= this.copy(money = start)
    fun add(change : BigDecimal) = this.copy(money = money + change, transfers = transfers+1)
}

fun processBankFacts(id: String, fact: AccountFact, obj: AllAccounts): AllAccounts = when (fact) {
    is MoneyTransfered  ->  obj.add(id, fact.amountDiff)
    is InitialTransfer -> obj.init(id, fact.amount)
}

class ObjReadSide<ID, FACT : Fact<*>, T>(val processor: (ID, FACT, T) -> T, inital: T) : ReadSideProcessor<ID, FACT, Unit> {
    private val reference = AtomicReference<T>(inital)

    override fun processFact(id: ID, fact: FACT, saved : SavedFact<Unit>) {
        reference.updateAndGet {
            processor(id, fact, it)
        }
    }

    fun getObject()  = reference.get()
}


