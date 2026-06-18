package com.umesh.unipay_1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RechargeOrderRequest {

    @NotNull(message = "Amount is required")
    @Min(value = 10, message = "Amount must be at least ₹10")
    @Max(value = 10000, message = "Amount cannot exceed ₹10000")
    private BigDecimal amount;
}
