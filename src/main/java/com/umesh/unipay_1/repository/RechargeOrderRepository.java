package com.umesh.unipay_1.repository;

import com.umesh.unipay_1.entity.RechargeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RechargeOrderRepository extends JpaRepository<RechargeOrder, Long> {
    Optional<RechargeOrder> findByRazorpayOrderId(String razorpayOrderId);
}
