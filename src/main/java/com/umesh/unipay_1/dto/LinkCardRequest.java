package com.umesh.unipay_1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LinkCardRequest {

    @NotBlank(message = "Card UID is required")
    private String cardUid;

    @NotNull(message = "User ID is required")
    private Long userId;
}
