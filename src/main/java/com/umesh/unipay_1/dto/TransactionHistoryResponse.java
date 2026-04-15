package com.umesh.unipay_1.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHistoryResponse {
    private boolean success;
    private TransactionPageData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionPageData {
        private List<TransactionSummary> transactions;
        private int totalPages;
        private long totalElements;
        private int currentPage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionSummary {
        private Long id;
        private String type;         // "NFC_PAYMENT" or "RECHARGE"
        private BigDecimal amount;
        private String status;
        private String direction;    // "DEBIT" or "CREDIT"
        private String counterparty;
        private String cardUid;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime createdAt;
    }
}
