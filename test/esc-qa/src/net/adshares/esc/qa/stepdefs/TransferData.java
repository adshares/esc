package net.adshares.esc.qa.stepdefs;

import java.math.BigDecimal;

public class TransferData {

    private BigDecimal amount;
    private BigDecimal fee;

    public TransferData() {
        this.amount = BigDecimal.ZERO;
        this.fee = BigDecimal.ZERO;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

}
