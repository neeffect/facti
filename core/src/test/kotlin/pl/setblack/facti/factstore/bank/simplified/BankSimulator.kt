package pl.setblack.facti.factstore.bank.simplified

import com.google.common.base.Preconditions
import pl.setblack.facti.factstore.repo.file.SimpleFileRepositoryFactory
import reactor.core.publisher.Flux
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Clock
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BankSimulator(
        val accounts: Int,
        val initAmount: Int,
        val transferMax: Int,
        val threads: Int,
        val tramsfersPerThread: Int,
        val loops: Int,
        val bank: SimpleBank) {

    private val accountPrefix = "acc"

    private val rnd = Random(41)
    private val executor = Executors.newFixedThreadPool(threads)


    fun initAccounts() {
        val startMoney = initAmount.toBigDecimal()
        val created = Flux.range(0, accounts).flatMap {
            bank.createAccount(accountId(it), startMoney)
        }.filter { it }.count()

        val nmb = created.block()
        if (nmb != accounts.toLong()) {
            throw IllegalStateException("wrong accounts number created ${nmb}")
        }

    }

    fun randomTransfer() {
        val from = accountId(rnd.nextInt(accounts))
        val to = accountId(rnd.nextInt(accounts))
        val amount = (rnd.nextInt(transferMax) + 1).toBigDecimal()
        bank.transfer(amount, from, to).blockLast()
    }


    fun simulate() {
        initAccounts()
        sanityCheck()
        val startTime = System.currentTimeMillis()
        for (i in 1..threads) {
            executor.submit { -> transferThread() }
            println("submitted ${i}")
        }
        println("shutdown")
        executor.shutdown()
        println("waiting")
        executor.awaitTermination(120, TimeUnit.SECONDS)

        val endTime = System.currentTimeMillis()

        sanityCheck()
        println("done checks 1.")
        bank.reload()
        sanityCheck()
        println("done checks 2.")
        val totalNumberOfTransfers = threads * loops * tramsfersPerThread
        val operationsPerSec = totalNumberOfTransfers.toDouble()*1000.0 / (endTime-startTime)
        println("total speed transfers per/sec = ${operationsPerSec}")
        System.exit(0)

    }


    private fun transferThread() {
        println("starting thread")
        try {
            for (i in 1..loops) {
                transferLoop()
                //sanityCheck()
                snapshots()
            }
            println("finished thread")
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun snapshots() {
        Flux.range(0, accounts).flatMap {
            bank.snapshot(accountId(it))
        }.blockLast()
        println("done snap")
    }

    private fun transferLoop() {
        Flux.range(0, tramsfersPerThread)
                .map {
                    randomTransfer()
                }.blockLast()
    }


    fun sanityCheck() {
        val result = Flux.range(0, accounts).flatMap {
            bank.getAccountTotal(accountId(it))
        }.reduce(0.toBigDecimal(), BigDecimal::add)
                .blockOptional().orElse(0.toBigDecimal())

        val expected = initAmount.toBigDecimal() * accounts.toBigDecimal()
        println("total amount: ${result}")
        Preconditions.checkState(expected.setScale(4) == result.setScale(4), "had ${result} expected ${expected}")

    }


    private fun accountId(id: Int) = "${accountPrefix}_${id}"

}

fun main(args: Array<String>) {
    println("started")
    val clock = Clock.systemDefaultZone()

    val tmpDir = Files.createTempDirectory("facti-bank-simulator")
    val simpleRepositoryFactory =
            SimpleFileRepositoryFactory<String, SimpleAccount, AccountFact>({ id: String -> SimpleAccount(id) }, tmpDir, clock)

    val repo = simpleRepositoryFactory.create()

    val bank = SimpleBank(repo)
    val simulator = BankSimulator(20, 100, 50, 5, 5000, 5, bank)
    simulator.simulate()

}