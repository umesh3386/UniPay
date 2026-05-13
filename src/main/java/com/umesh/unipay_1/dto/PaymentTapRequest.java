package com.umesh.unipay_1.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentTapRequest {

    @NotBlank(message = "Card UID is required")
    private String cardUid;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum transaction is ₹1")
    @DecimalMax(value = "5000.00", message = "Maximum transaction is ₹5000")
    @Digits(integer = 10, fraction = 4, message = "Amount can have at most 4 decimal places")
    private BigDecimal amount;


    @NotNull(message = "Merchant ID is required")
    private Long merchantId;
}
