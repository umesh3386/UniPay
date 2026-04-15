package com.umesh.unipay_1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CardActionRequest {

    @NotBlank(message = "Action is required (e.g. 'activate' or 'deactivate')")
    private String action;

    private String reason; // Used for audit trails during deactivation
}
