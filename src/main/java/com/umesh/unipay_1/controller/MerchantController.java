package com.umesh.unipay_1.controller;

import com.umesh.unipay_1.dto.MerchantProfileResponse;
import com.umesh.unipay_1.dto.TransactionHistoryResponse;
import com.umesh.unipay_1.dto.WalletBalanceResponse;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.exception.UnauthorizedException;
import com.umesh.unipay_1.service.MerchantService;
import com.umesh.unipay_1.service.TransactionService;
import com.umesh.unipay_1.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
@Tag(name = "Merchant Self-Service", description = "Dashboard operations for logged-in merchants")
@SecurityRequirement(name = "bearerAuth")
public class MerchantController {

    private final MerchantService merchantService;
    private final WalletService walletService;
    private final TransactionService transactionService;

    @GetMapping("/profile")
    @PreAuthorize("hasRole('MERCHANT')")
    @Operation(summary = "Get Merchant Profile", description = "Retrieves business name, total earnings, and account status.")
    public ResponseEntity<MerchantProfileResponse> getProfile(Authentication authentication) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(merchantService.getMerchantDashboardProfile(user));
    }

    @GetMapping("/balance")
    @PreAuthorize("hasRole('MERCHANT')")
    @Operation(summary = "Get Merchant Wallet Balance", description = "Retrieves the current wallet balance of the authenticated merchant.")
    public ResponseEntity<WalletBalanceResponse> getBalance(Authentication authentication) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(walletService.getBalance(user));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('MERCHANT')")
    @Operation(summary = "Get Merchant Transactions", description = "Retrieves paginated history of payments received by this merchant.")
    public ResponseEntity<TransactionHistoryResponse> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Authentication authentication
    ) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(transactionService.getTransactionHistory(user, page, size, type, from, to));
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User)) {
            throw new UnauthorizedException("Authentication required");
        }
        return (User) authentication.getPrincipal();
    }
}
