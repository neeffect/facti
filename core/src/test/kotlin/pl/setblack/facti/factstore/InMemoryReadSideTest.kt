package pl.setblack.facti.factstore

import io.kotlintest.shouldBe
import io.kotlintest.specs.DescribeSpec
import io.vavr.control.Option
import pl.setblack.facti.factstore.mem.InMemFactStore
import pl.setblack.facti.factstore.repo.SavedFact
import reactor.test.StepVerifier


internal class InMemoryReadSideTest : DescribeSpec({
    describe("in memory read side projection") {
        val accumulator = { x: Int, acc: Option<Int>, fact: Acc ->  acc.getOrElse(0) + fact.value }


        it ("should process single fact") {
            val factStore = InMemFactStore<Int, Acc>()
            val readSide = InMemoryReadSide<Int, Int, Acc>(factStore, accumulator)
            readSide.processFact(14,  SavedFact(0, Unit, Acc(7)))
            readSide.processFact(14,  SavedFact(0, Unit, Acc(5)))
            StepVerifier.create(readSide.recentProjection(14 )).assertNext  {
                it.projection shouldBe 12
            }.verifyComplete()
        }

        it ("should restore projection") {
            val factStore = InMemFactStore<Int, Acc>()
            val readSide = InMemoryReadSide<Int, Int, Acc>(factStore, accumulator)
            factStore.persist(14, Acc(7))
            factStore.persist(14, Acc(2))
            //readSide.processFact(14, Acc(7), SavedFact(0, Unit))

            StepVerifier.create(readSide.recentProjection(14 )).assertNext{
                it.projection shouldBe (9)
            }.verifyComplete()
        }
    }
})



internal class Acc(val value: Int) : Fact<Int> {
    override fun apply(state: Int): Int {
        TODO("apply not implemented")
    }
}