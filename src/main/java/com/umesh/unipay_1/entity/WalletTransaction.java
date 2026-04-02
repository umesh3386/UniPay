package com.umesh.unipay_1.entity;

import com.umesh.unipay_1.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType direction; // changed from type to direction (CREDIT or DEBIT)

    @Column(name = "transaction_category", length = 50)
    private String transactionCategory; // e.g. "NFC_PAYMENT" or "RECHARGE"

    @Column(name = "counterparty_name", length = 255)
    private String counterpartyName; // Target / Payer string

    @Column(name = "card_uid", length = 50)
    private String cardUid; // Related NFC Card (if applicable)

    @Column(length = 50)
    private String status; // Usually SUCCESS for ledgers


    @Column(length = 255)
    private String reference; // e.g., "Recharge Order: order_XYZ"

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
