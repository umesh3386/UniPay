package com.umesh.unipay_1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterCardRequest {

    @NotBlank(message = "Card UID is required")
    private String cardUid;
}
