package pl.setblack.facti.factstore.bank.full

import io.vavr.control.Option
import pl.setblack.facti.factstore.Fact
import java.math.BigDecimal

class CBank {
}

data class Account(val amount : BigDecimal) {
}



data class TransferState (val transfer : Option<Transfer>)

data class Transfer ( val id: String, val from :String, val to : String, val amount: BigDecimal)


data class TransferFact(val transfer :Transfer) : Fact<TransferState> {
    override fun apply(state: TransferState): TransferState {
        TODO("apply not implemented")
    }
}