package com.umesh.unipay_1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank(message="Refresh Token is required.")
    private String refreshToken;
}
