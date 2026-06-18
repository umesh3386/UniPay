package com.umesh.unipay_1.controller;

import com.umesh.unipay_1.dto.MessageResponse;
import com.umesh.unipay_1.dto.RechargeOrderRequest;
import com.umesh.unipay_1.dto.RechargeOrderResponse;
import com.umesh.unipay_1.dto.VerifyPaymentRequest;
import com.umesh.unipay_1.dto.WalletBalanceResponse;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.exception.UnauthorizedException;
import com.umesh.unipay_1.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Wallet balance and Razorpay top-up operations")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/balance")
    @Operation(summary = "Get Wallet Balance", description = "Retrieves the current wallet balance of the authenticated user.")
    public ResponseEntity<WalletBalanceResponse> getBalance(Authentication authentication) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(walletService.getBalance(user));
    }

    @PostMapping("/create-recharge-order")
    @Operation(summary = "Create Wallet Recharge Order", description = "Initiates a Razorpay order intent (minimum ₹10).")
    public ResponseEntity<RechargeOrderResponse> createRechargeOrder(
            @Valid @RequestBody RechargeOrderRequest request,
            Authentication authentication
    ) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(walletService.createRechargeOrder(user, request));
    }

    @PostMapping("/verify-payment")
    @Operation(summary = "Verify Wallet Recharge Payment", description = "Verifies Razorpay signature directly to finalize credit to the wallet atomically.")
    public ResponseEntity<MessageResponse> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request,
            Authentication authentication
    ) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(walletService.verifyRechargePayment(user, request));
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User)) {
            throw new UnauthorizedException("Authentication required");
        }
        return (User) authentication.getPrincipal();
    }
}
