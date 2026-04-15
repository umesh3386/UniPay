package com.umesh.unipay_1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
    private LocalDateTime timestamp=LocalDateTime.now();

public ErrorResponse(String code, String message) {
    this.code = code;
    this.message = message;
    this.timestamp=LocalDateTime.now();
}
}
