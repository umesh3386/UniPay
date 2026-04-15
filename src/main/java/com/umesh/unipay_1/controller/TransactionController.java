 package com.umesh.unipay_1.controller;

import com.umesh.unipay_1.dto.TransactionDetailResponse;
import com.umesh.unipay_1.dto.TransactionHistoryResponse;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.exception.UnauthorizedException;
import com.umesh.unipay_1.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Endpoints for viewing unified ledger history and details")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/history")
    @Operation(summary = "Get Paginated Transaction History", description = "Query wallet ledger natively with optional filters for type and timeframe.")
    public ResponseEntity<TransactionHistoryResponse> getTransactionHistory(
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

    @GetMapping("/{id}")
    @Operation(summary = "Get Single Transaction Detail", description = "Retrieves granular information behind a specific ledger log entry.")
    public ResponseEntity<TransactionDetailResponse> getTransactionDetail(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(transactionService.getTransactionDetail(id, user));
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User)) {
            throw new UnauthorizedException("Authentication required");
        }
        return (User) authentication.getPrincipal();
    }
}
