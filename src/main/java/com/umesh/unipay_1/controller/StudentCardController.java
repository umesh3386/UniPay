package com.umesh.unipay_1.controller;

import com.umesh.unipay_1.dto.CardStatusResponse;
import com.umesh.unipay_1.dto.MyCardsResponse;
import com.umesh.unipay_1.dto.BlockCardRequest;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.exception.UnauthorizedException;
import com.umesh.unipay_1.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Tag(name = "Student Card APIs", description = "Card status operations for authenticated students — " +
        "a student can only activate or deactivate their own linked card")
@SecurityRequirement(name = "bearerAuth")
public class StudentCardController {

    private final CardService cardService;

    /**
     * PATCH /api/cards/{cardUid}
     * Allows a STUDENT to toggle their own linked card.
     * Returns 403 if the card belongs to a different user.
     */
    @PatchMapping("/{cardUid}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Toggle my Card Status (STUDENT only)",
            description = "Activates or deactivates the card identified by cardUid securely utilizing a JSON request body mapping the specific 'action' and logging 'reason'. " +
                          "The card must be linked to the authenticated student."
    )
    public ResponseEntity<CardStatusResponse> toggleMyCard(
            @PathVariable String cardUid,
            @RequestBody com.umesh.unipay_1.dto.CardActionRequest request,
            Authentication authentication
    ) {
        User student = resolveUser(authentication);
        boolean activate;

        if ("activate".equalsIgnoreCase(request.getAction())) {
            activate = true;
        } else if ("deactivate".equalsIgnoreCase(request.getAction())) {
            activate = false;
        } else {
            throw new com.umesh.unipay_1.exception.BusinessException("Invalid action. Must be 'activate' or 'deactivate'.");
        }

        return ResponseEntity.ok(cardService.setMyCardStatus(cardUid, student, activate, request.getReason()));
    }


    /**
     * GET /api/cards/my-cards
     * Get logged-in user's NFC cards.
     */
    @GetMapping("/my-cards")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Get My Cards (STUDENT only)",
            description = "Returns all NFC cards linked to the authenticated student."
    )
    public ResponseEntity<MyCardsResponse> getMyCards(Authentication authentication) {
        User student = resolveUser(authentication);
        return ResponseEntity.ok(cardService.getMyCards(student));
    }

    /**
     * PATCH /api/cards/{cardUid}/block
     * Allows a STUDENT to block their own card.
     */
    @PatchMapping("/{cardUid}/block")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Block my Card (STUDENT only)",
            description = "Marks the card as blocked (lost/stolen). The card must be linked to the authenticated student."
    )
    public ResponseEntity<CardStatusResponse> blockMyCard(
            @PathVariable String cardUid,
            @RequestBody(required = false) BlockCardRequest request,
            Authentication authentication
    ) {
        User student = resolveUser(authentication);
        String reason = (request != null) ? request.getReason() : "Blocked by student";
        return ResponseEntity.ok(cardService.blockMyCard(cardUid, student, reason));
    }

    /**
     * PATCH /api/cards/{cardUid}/unblock
     * Allows a STUDENT to unblock their own card.
     */
    @PatchMapping("/{cardUid}/unblock")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Unblock my Card (STUDENT only)",
            description = "Removes the blocked status from the card. The card must be linked to the authenticated student."
    )
    public ResponseEntity<CardStatusResponse> unblockMyCard(
            @PathVariable String cardUid,
            Authentication authentication
    ) {
        User student = resolveUser(authentication);
        return ResponseEntity.ok(cardService.unblockMyCard(cardUid, student));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User)) {
            throw new UnauthorizedException("Authentication required");
        }
        return (User) authentication.getPrincipal();
    }
}
