package pl.setblack.facti.factstore.bank.simplified

import io.vavr.collection.Array
import io.vavr.collection.Seq
import java.math.BigDecimal

data class SimpleAccount(
    val id: String,
    val initial: BigDecimal = BigDecimal.ZERO,
    val total: BigDecimal = BigDecimal.ZERO,
    val transfers: Seq<AccountChange> = Array.empty()) {
    fun change(amountDiff: BigDecimal, otherAccountId: String): SimpleAccount =
        copy(transfers = transfers.append(AccountChange(amountDiff, otherAccountId)), total = total + amountDiff)

    fun totalValue() = transfers.foldLeft(initial) { sum, transfer -> sum.add(transfer.amount) }

    fun withInitial(amount: BigDecimal): SimpleAccount {
        return copy(initial = amount, total = amount)
    }

}

data class AccountChange(val amount: BigDecimal = BigDecimal.ZERO, val otherAccountId: String) {
}


