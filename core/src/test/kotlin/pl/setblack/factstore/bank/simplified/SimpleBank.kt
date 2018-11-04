package pl.setblack.factstore.bank.simplified

import io.vavr.Tuple
import io.vavr.Tuple2
import io.vavr.control.Either
import pl.setblack.factstore.*
import pl.setblack.factstore.file.BadRegistry
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal


class SimpleBank(val  repo : Repository<String, SimpleAccount, AccountFact>) {

    fun createAccount(id: String, initialAmount : BigDecimal) : Mono<Boolean> {
        return repo.execute(id, InitAccount(initialAmount)).last()
    }

    fun transfer(amount:BigDecimal, from : String, to : String) : Flux<TransferResult>{
        val withdraw = WithdrawMoney(amount, to)
        val deposit= DepositMoney(from, amount)
        val transfered = repo.execute(from,withdraw)
        val done = transfered.flatMap {
            it.map {
                repo.execute(to, deposit)
            }.getOrElseGet { l ->
                Mono.just(Either.left<TranferError, TransferDone>(l) as TransferResult).flux()
            }
        }
        return done
    }


    fun getAccountTotal( id: String) : Flux<BigDecimal> {
        return repo.query(id, ReadAccount).map {
            it.total
        }
    }

    fun reload() {
        if (repo is IOManager) {
            repo.restart()
        }
    }

    fun snapshot(account: String): Mono<SimpleAccount> {
        return repo.snapshot(account)
    }
}



sealed class AccountFact : Fact<SimpleAccount>

data class MoneyTransfered(val amountDiff: BigDecimal, val otherAccountId: String) : AccountFact() {
    override fun apply(state: SimpleAccount): SimpleAccount =
            state.change(amountDiff, otherAccountId)
}

data class InitialTransfer(val amount : BigDecimal) : AccountFact() {
    override fun apply(state: SimpleAccount): SimpleAccount =
        state.withInitial(amount)

}

sealed class
AccountCommand<R> : Command<SimpleAccount, AccountFact, R>

abstract class AccountQuery<R> : AccountCommand<R>(), Query<SimpleAccount, AccountFact, R>

data class WithdrawMoney(val amount: BigDecimal, val otherAccountId: String) : AccountCommand<TransferResult>(){
    override fun apply(stateBefore: SimpleAccount): Tuple2<(SimpleAccount) -> Flux<TransferResult>, Flux<AccountFact>> {
        return if (amount > 0.toBigDecimal()){
            if (amount <= stateBefore.total) {
            Tuple.of({ newState -> Mono.just(Either.right<TranferError, TransferDone>(TransferDone(newState.transfers.size()))).flux() },
                    Mono.just<AccountFact>(MoneyTransfered(amount.negate(), otherAccountId)).flux())
            } else {
                Tuple.of({ _ -> Mono.just(Either.left<TranferError, TransferDone>(TranferError.NOT_ENOUGH_MONEY)).flux() }, Flux.empty<AccountFact>())
            }
        } else {
            Tuple.of({ _ -> Mono.just(Either.left<TranferError, TransferDone>(TranferError.WRONG_AMOUNT)).flux() }, Flux.empty<AccountFact>())
        }
    }
}

data class DepositMoney(val otherAccountId: String, val amount: BigDecimal) : AccountCommand<TransferResult>() {

    override fun apply(stateBefore: SimpleAccount): Tuple2<(SimpleAccount) -> Flux<TransferResult>, Flux<AccountFact>> {
        return if (amount > 0.toBigDecimal()){
            Tuple.of({ newState -> Mono.just(Either.right<TranferError, TransferDone>(TransferDone(newState.transfers.size()))).flux() },
                    Mono.just<AccountFact>(MoneyTransfered(amount, otherAccountId)).flux())
        } else {
            Tuple.of({ _ -> Mono.just(Either.left<TranferError, TransferDone>(TranferError.WRONG_AMOUNT)).flux() }, Flux.empty<AccountFact>())
        }
    }
}

data class InitAccount(val amount : BigDecimal) : AccountCommand<Boolean>() {
    override fun apply(stateBefore: SimpleAccount): Tuple2<(SimpleAccount) -> Flux<Boolean>, Flux<AccountFact>> {
        if ( stateBefore.initial == BigDecimal.ZERO && stateBefore.transfers.isEmpty) {
            return Tuple.of ( {account -> Mono.just(account.initial == amount).flux()} , Mono.just<AccountFact>(InitialTransfer(amount)).flux())
        } else {
            return Tuple.of ( {_ -> Mono.just(false).flux()}, Flux.empty<AccountFact>())
        }
    }
}

object ReadAccount : AccountQuery<SimpleAccount>() {
    override fun query(stateBefore: SimpleAccount): Flux<SimpleAccount> {
        return Mono.just(stateBefore).flux()
    }
}


typealias TransferResult = Either<TranferError, TransferDone>

data class TransferDone(val transactionId: Int)

enum class TranferError {
    WRONG_AMOUNT,
    NOT_ENOUGH_MONEY
}


