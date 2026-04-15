package com.umesh.unipay_1.controller;

import com.umesh.unipay_1.dto.*;
import com.umesh.unipay_1.security.authorization.SuperAdminOnly;
import com.umesh.unipay_1.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/cards")
@RequiredArgsConstructor
@Tag(name = "Card APIs", description = "Card management operations — requires ADMIN role")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

    private final CardService cardService;

    /**
     * POST /api/admin/cards
     * Register a new physical NFC/RFID card in the system.
     * Cards must be registered before they can be linked to a user.
     */
    @PostMapping
    @SuperAdminOnly
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Register a new Card (ADMIN only)",
            description = "Adds a new NFC/RFID card to the system by its UID. " +
                          "The card is created in an unlinked state and can later be assigned to a user."
    )
    public ResponseEntity<LinkCardResponse.CardData> registerCard(
            @Valid @RequestBody RegisterCardRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(cardService.registerCard(request.getCardUid()));
    }

    /**
     * POST /api/admin/cards/link
     * Link an existing card to a user.
     */
    @PostMapping("/link")
    @SuperAdminOnly
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Link Card to User (ADMIN only)",
            description = "Associates an existing registered card (by cardUid) with a user (by userId). " +
                          "Validates card is active and user is active and not blocked. " +
                          "If the card is already linked to another user, it will be re-assigned."
    )
    public ResponseEntity<LinkCardResponse> linkCard(
            @Valid @RequestBody LinkCardRequest request
    ) {
        return ResponseEntity.ok(cardService.linkCard(request));
    }

    /**
     * DELETE /api/admin/cards/{cardUid}/unlink
     * Remove the link between a card and its current user.
     */
    @DeleteMapping("/{cardUid}/unlink")
    @SuperAdminOnly
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Unlink Card from User (ADMIN only)",
            description = "Removes the association between the specified card and its current user. " +
                          "The card remains registered in the system in an unlinked state."
    )
    public ResponseEntity<LinkCardResponse> unlinkCard(
            @PathVariable String cardUid
    ) {
        return ResponseEntity.ok(cardService.unlinkCard(cardUid));
    }

    /**
     * PATCH /api/admin/cards/{cardUid}/activate
     * Mark any card as ACTIVE (ADMIN only).
     */
    @PatchMapping("/{cardUid}/activate")
    @SuperAdminOnly
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Activate a Card (ADMIN only)",
            description = "Marks the specified card as active. An active card can be used for transactions."
    )
    public ResponseEntity<CardStatusResponse> activateCard(
            @PathVariable String cardUid
    ) {
        return ResponseEntity.ok(cardService.setCardStatus(cardUid, true));
    }

    /**
     * PATCH /api/admin/cards/{cardUid}/deactivate
     * Mark any card as INACTIVE (ADMIN only).
     */
    @PatchMapping("/{cardUid}/deactivate")
    @SuperAdminOnly
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Deactivate a Card (ADMIN only)",
            description = "Marks the specified card as inactive. An inactive card cannot be used for transactions "
                        + "and cannot be re-linked to a new user until re-activated."
    )
    public ResponseEntity<CardStatusResponse> deactivateCard(
            @PathVariable String cardUid
    ) {
        return ResponseEntity.ok(cardService.setCardStatus(cardUid, false));
    }
}
