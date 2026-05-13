package com.umesh.unipay_1.controller;

import com.umesh.unipay_1.dto.PaymentStatusResponse;
import com.umesh.unipay_1.dto.PaymentTapRequest;
import com.umesh.unipay_1.dto.PaymentTapResponse;
import com.umesh.unipay_1.dto.RefundRequest;
import com.umesh.unipay_1.dto.RefundResponse;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.exception.UnauthorizedException;
import com.umesh.unipay_1.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Core NFC tap payment and transaction status operations")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/tap")
    @PreAuthorize("hasRole('MERCHANT')")
    @Operation(summary = "Process NFC Card Tap", description = "Atomic payment transfer from the owner of the tapped card to the authenticated merchant.")
    public ResponseEntity<PaymentTapResponse> processTap(
            @Valid @RequestBody PaymentTapRequest request,
            Authentication authentication
    ) {
        User merchant = resolveUser(authentication);
        return ResponseEntity.ok(paymentService.processNfcPayment(merchant, request));
    }

    @GetMapping("/status/{id}")
    @Operation(summary = "Get Payment Status", description = "Retrieves the status of a specific payment transaction by ID. Only involved users can view.")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(paymentService.getPaymentStatus(id, user));
    }

    @PostMapping("/refund")
    @PreAuthorize("hasRole('MERCHANT')")
    @Operation(summary = "Process Partial/Full Refund", description = "Safely reverses a specific payment amount from the Merchant wallet back to the Student. Prevents double-refunding.")
    public ResponseEntity<RefundResponse> processRefund(
            @Valid @RequestBody RefundRequest request,
            Authentication authentication
    ) {
        User merchant = resolveUser(authentication);
        return ResponseEntity.ok(paymentService.processRefund(merchant, request));
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User)) {
            throw new UnauthorizedException("Authentication required");
        }
        return (User) authentication.getPrincipal();
    }
}
