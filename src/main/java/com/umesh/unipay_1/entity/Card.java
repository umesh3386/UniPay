package com.umesh.unipay_1.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "cards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Physical NFC/RFID card UID (e.g. "A3F9C21B").
     * Must be unique across the system.
     */
    @Column(name = "card_uid", nullable = false, unique = true, length = 50)
    private String cardUid;

    /**
     * The user this card is linked to.
     * Nullable — a card can exist in the system before being assigned.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Timestamp when this card was linked to the current user.
     */
    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    /**
     * Whether this card is currently active.
     * An inactive card cannot be used for transactions.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /**
     * Whether this card is blocked (e.g. reported lost/stolen).
     */
    @Column(name = "is_blocked", nullable = false)
    @Builder.Default
    private boolean isBlocked = false;

    /**
     * Reason for blocking the card.
     */
    @Column(name = "blocked_reason")
    private String blockedReason;

    @Column(name = "deactivated_reason", length = 255)
    private String deactivatedReason;

    @Column(name = "deactivated_by_user_id")
    private Long deactivatedByUserId;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /**
     * Timestamp of the last successful transaction/usage.
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;


    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
