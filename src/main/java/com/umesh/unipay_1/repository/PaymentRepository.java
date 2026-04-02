package com.umesh.unipay_1.repository;

import com.umesh.unipay_1.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.merchant.id = :merchantId AND p.status = 'SUCCESS'")
    BigDecimal sumTotalReceivedByMerchantId(@Param("merchantId") Long merchantId);
}

