package com.umesh.unipay_1.controller;

import com.umesh.unipay_1.dto.RegisterMerchantRequest;
import com.umesh.unipay_1.dto.UserActionRequest;
import com.umesh.unipay_1.dto.UserDetailResponse;
import com.umesh.unipay_1.dto.UserListResponse;
import com.umesh.unipay_1.dto.UserProfileResponse;
import com.umesh.unipay_1.dto.UserResponse;
import com.umesh.unipay_1.enums.Role;
import com.umesh.unipay_1.security.authorization.SuperAdminOnly;
import com.umesh.unipay_1.service.MerchantService;
import com.umesh.unipay_1.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin APIs", description = "Admin-only operations — requires ADMIN role")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {
    private final MerchantService merchantService;
    private final UserService userService;

    // ==========================================
    // MERCHANT MANAGEMENT
    // ==========================================
    /**
     * POST /api/admin/merchants/register
     *
     * Register a new MERCHANT account.
     * Protected at two layers:
     *   1. URL-level: SecurityConfig enforces ROLE_ADMIN on /api/admin/**
     *   2. Method-level: @SuperAdminOnly AOP annotation (double guard)
     */
    @PostMapping("/merchants/register")
    @SuperAdminOnly
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Register a new Merchant (ADMIN only)",
            description = "Creates a Firebase account and a DB record with Role=MERCHANT. " +
                          "Only accessible by authenticated users with the ADMIN role."
    )
    public ResponseEntity<UserResponse> registerMerchant(
            @Valid @RequestBody RegisterMerchantRequest request
    ) {
        UserResponse merchant = merchantService.registerMerchant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(merchant);
    }

    /**
     * GET /api/admin/merchants
     *
     * Retrieve a list of all registered merchants.
     * Protected at two layers — same as register.
     */
    @GetMapping("/merchants")
    @SuperAdminOnly
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "List all Merchants (ADMIN only)",
            description = "Returns all users with Role=MERCHANT."
    )
    public ResponseEntity<List<UserResponse>> getAllMerchants() {
        return ResponseEntity.ok(merchantService.getAllMerchants());
    }

    // ==========================================
    // GLOBAL USER MANAGEMENT
    // ==========================================

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all Users via Pagination", description = "Fetch a paginated list of all users, optionally filtered by Role.")
    public ResponseEntity<UserListResponse> getAllUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(userService.getAllUsersPaginated(role, page, size));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get deep User Details", description = "Deeply inspects a User, aggregating their raw profile with wallet and card telemetry.")
    public ResponseEntity<UserDetailResponse> getUserDetails(@PathVariable Long id) {
        UserProfileResponse profileData = userService.getUserDetails(id);
        return ResponseEntity.ok(UserDetailResponse.builder()
                .success(true)
                .data(profileData.getData())
                .build());
    }

    @PatchMapping("/users/{id}/block")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Global Session Block", description = "Natively locks the requested user out of performing any Unipay transactions and disables card mapping recursively.")
    public ResponseEntity<UserResponse> blockUser(
            @PathVariable Long id,
            @RequestBody(required = false) UserActionRequest request
    ) {
        String reason = (request != null) ? request.getReason() : null;
        return ResponseEntity.ok(userService.blockUser(id, reason));
    }

    @PatchMapping("/users/{id}/unblock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Global Session Unblock", description = "Recursively removes blocks from a specific user, reinstating access to the Unipay ecosystem natively.")
    public ResponseEntity<UserResponse> unblockUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.unblockUser(id));
    }
}

