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
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private String role;
    private String studentId;
    private String businessName;
    private Boolean isActive;
    private Boolean isBlocked;
    private LocalDateTime createdAt;
}