package com.umesh.unipay_1.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundRequest {

    @NotNull(message = "Original transaction ID is required")
    private Long transactionId;

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.0001", message = "Refund amount must be greater than 0")
    @Digits(integer = 10, fraction = 4, message = "Amount can have at most 4 decimal places")
    private BigDecimal amount;
}
