package pl.setblack.facti.factstore

import io.kotlintest.specs.DescribeSpec
import pl.setblack.facti.factstore.mem.InMemFactStore
import reactor.core.publisher.Mono


internal class InMemoryReadSideTest : DescribeSpec({
    describe("in memory read side projection") {
        val accumulator = { x: Int, acc: Int, fact: Acc -> Mono.just( acc + fact.value) }
        val factStore = InMemFactStore<Int, Acc>()
        val readSide = InMemoryReadSide<Int, Int, Acc>(factStore, accumulator)

        it ("should process single fact") {
            factStore.persist(0, Acc(7))
        }
    }
})



internal class Acc(val value: Int) : Fact<Int> {
    override fun apply(state: Int): Int {
        TODO("apply not implemented")
    }
}