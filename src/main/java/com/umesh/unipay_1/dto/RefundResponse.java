package com.umesh.unipay_1.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {
    private boolean success;
    private String message;
    private RefundData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundData {
        private Long refundTransactionId;
        private Long originalTransactionId;
        private BigDecimal refundedAmount;
        private BigDecimal remainingAmount;
        private String merchantBalance;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime refundedAt;
    }
}
