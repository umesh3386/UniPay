package com.umesh.unipay_1.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CardStatusResponse {
    private boolean success;
    private String message;
    private String cardUid;
    private boolean isActive;
    private boolean isBlocked;
    private String linkedTo;
    private LocalDateTime updatedAt;
}
