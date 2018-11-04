package pl.setblack.facti.factstore.repo


import io.kotlintest.shouldBe
import io.kotlintest.specs.DescribeSpec
import io.vavr.collection.Array
import pl.setblack.facti.factstore.bank.simplified.AccountFact
import pl.setblack.facti.factstore.bank.simplified.DepositMoney
import pl.setblack.facti.factstore.bank.simplified.ReadAccount
import pl.setblack.facti.factstore.bank.simplified.SimpleAccount
import pl.setblack.facti.factstore.file.FileFactStore
import pl.setblack.facti.factstore.file.FileSnapshotStore
import pl.setblack.facti.factstore.repo.simple.SimpleRepository
import pl.setblack.facti.factstore.util.SimpleTaskHandler
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

const val czeslawAccountId = "konto_czeslawa"
const val krzybiuAccountId = "konto_krzybia"

class SimpleRepositoryTest : DescribeSpec({
    describe("simple accounts repository") {
        val timeZone = TimeZone.getTimeZone("GMT+0:00")
        val initialTime = LocalDateTime.parse("2018-10-01T10:00")
        val clock = Clock.fixed(initialTime.atZone(timeZone.toZoneId()).toInstant(), timeZone.toZoneId())
        val tmpDir = Files.createTempDirectory("facti-repo-test")
        val tasksHandler = SimpleTaskHandler()
        val factStore = FileFactStore<String, AccountFact>(tmpDir, clock, tasksHandler )
        val snapshotStore = FileSnapshotStore<String, SimpleAccount>(tmpDir, clock, tasksHandler)

        val simpleRepository = SimpleRepository(
                { SimpleAccount(it) }, factStore, snapshotStore, tasksHandler
        )

        context("for an empty repo") {
            it("should receive initial account") {
                val initialAccount = simpleRepository.query(czeslawAccountId, ReadAccount)
                StepVerifier.create(initialAccount)
                        .assertNext { it.initial shouldBe 0.toBigDecimal() }
                        .verifyComplete()
            }
        }

        context("after 2 changes") {
            val commands = Array.range(0, 3)
                    .map { DepositMoney(krzybiuAccountId, BigDecimal.valueOf(it.toLong())) }
                    .map { simpleRepository.execute(czeslawAccountId, it) }

            val allCommands = Flux.concat(commands)


            it("should  succeed on last 2 transfers") {
                simpleRepository.deleteAll()
                StepVerifier.create(allCommands)
                        .expectNextCount(1)
                        .assertNext { it.isRight shouldBe (true) }
                        .assertNext { it.isRight shouldBe (true) }
                        .verifyComplete()
            }
            it("should fail on first  (0 amount)  transfer") {
                simpleRepository.deleteAll()
                StepVerifier.create(allCommands)
                        .assertNext { it.isRight shouldBe (false) }
                        .expectNextCount(2)
                        .verifyComplete()
            }
            it("should return account with 2 transfers") {
                simpleRepository.deleteAll()

                val allOperations = allCommands.last().flatMap { _ ->
                    simpleRepository.query(czeslawAccountId, ReadAccount).single()
                }
                StepVerifier.create(allOperations)
                        .assertNext { it.transfers.size() shouldBe (2) }
                        .verifyComplete()

            }
        }
        context("restore events") {
            simpleRepository.deleteAll()
            val commands = Array.range(1, 5)
                    .map { DepositMoney(krzybiuAccountId, BigDecimal.valueOf(it.toLong())) }
                    .map { simpleRepository.execute(czeslawAccountId, it) }
            val allCommands = Flux.concat(commands)
            allCommands.blockLast()

            it("we have 4 transfers before restart") {
                val restored = simpleRepository.query(czeslawAccountId, ReadAccount).single()
                StepVerifier.create(restored)
                        .assertNext { it.transfers.size() shouldBe (4) }
                        .verifyComplete()
            }

            it("shall restore events ") {
                simpleRepository.restart()
                val restored = simpleRepository.query(czeslawAccountId, ReadAccount).single()
                StepVerifier.create(restored)
                        .assertNext { it.transfers.size() shouldBe (4) }
                        .verifyComplete()
            }
        }
        context("simple snapshot") {
            simpleRepository.deleteAll()
            val commands = Array.range(1, 5)
                    .map { DepositMoney(krzybiuAccountId, BigDecimal.valueOf(it.toLong())) }
                    .map { simpleRepository.execute(czeslawAccountId, it) }

            val changeCommands = Flux.concat(commands)
            val snapshot = simpleRepository.snapshot(czeslawAccountId).flux()
            val all = Flux.concat(changeCommands, snapshot)

            it("should restore snapshot") {
                simpleRepository.deleteAll()
                all.blockLast()
                simpleRepository.restart()
                val restored = simpleRepository.query(czeslawAccountId, ReadAccount).single()
                StepVerifier.create(restored)
                        .assertNext { it.transfers.size() shouldBe (4) }
                        .verifyComplete()
            }

            it("should restore events snapshot") {
                simpleRepository.deleteAll()
                all.blockLast()
                val nextCommands = Array.range(5, 9)
                        .map { DepositMoney(krzybiuAccountId, BigDecimal.valueOf(it.toLong())) }
                        .map { simpleRepository.execute(czeslawAccountId, it) }
                Flux.concat(nextCommands).blockLast()
                val restored = simpleRepository.query(czeslawAccountId, ReadAccount).single()
                StepVerifier.create(restored)
                        .assertNext { it.transfers.size() shouldBe (8) }
                        .verifyComplete()
            }

            it("should restore events after 2 snapshots") {
                simpleRepository.deleteAll()
                all.blockLast()
                all.blockLast()

                val restored = simpleRepository.query(czeslawAccountId, ReadAccount).single()
                StepVerifier.create(restored)
                        .assertNext { it.transfers.size() shouldBe (8) }
                        .verifyComplete()
            }

        }
    }


})