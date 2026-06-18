package com.umesh.unipay_1.entity;

import com.umesh.unipay_1.enums.RechargeOrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recharge_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RechargeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "razorpay_order_id", nullable = false, unique = true)
    private String razorpayOrderId;

    @Column(nullable = false)
    private BigDecimal amount; // in INR

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RechargeOrderStatus status;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature")
    private String razorpaySignature;

    @Column(name = "receipt_id")
    private String receiptId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
