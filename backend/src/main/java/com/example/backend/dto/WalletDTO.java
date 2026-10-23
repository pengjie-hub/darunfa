package com.example.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 钱包响应 DTO：当前余额 + 最近的余额流水。
 */
public class WalletDTO {

    private BigDecimal balance;
    private List<BalanceTransactionDTO> transactions;

    public WalletDTO() {
    }

    public WalletDTO(BigDecimal balance, List<BalanceTransactionDTO> transactions) {
        this.balance = balance;
        this.transactions = transactions;
    }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public List<BalanceTransactionDTO> getTransactions() { return transactions; }
    public void setTransactions(List<BalanceTransactionDTO> transactions) { this.transactions = transactions; }
}
