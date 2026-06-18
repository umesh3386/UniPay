package com.umesh.unipay_1.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RechargeOrderResponse {
    private boolean success;
    private OrderData data;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderData {
        private String orderId;
        private Long amount; // in paise
        private String currency;
        private String razorpayKeyId;
        private String receipt;
    }
}
