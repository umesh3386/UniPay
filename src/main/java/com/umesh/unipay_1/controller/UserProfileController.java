package com.umesh.unipay_1.controller;

import com.umesh.unipay_1.dto.UpdateProfileRequest;
import com.umesh.unipay_1.dto.UserProfileResponse;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.exception.UnauthorizedException;
import com.umesh.unipay_1.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Endpoints for fetching and updating user's own profile")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get User Profile", description = "Retrieves the rich profile data for the currently authenticated user.")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(userService.getProfile(user));
    }

    @PutMapping
    @Operation(summary = "Update User Profile", description = "Updates optional fields like fullName or phone number.")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication
    ) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(userService.updateProfile(user, request));
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User)) {
            throw new UnauthorizedException("Authentication required");
        }
        return (User) authentication.getPrincipal();
    }
}
