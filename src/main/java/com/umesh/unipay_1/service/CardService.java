package com.umesh.unipay_1.service;

import com.umesh.unipay_1.dto.LinkCardRequest;
import com.umesh.unipay_1.dto.LinkCardResponse;
import com.umesh.unipay_1.dto.CardStatusResponse;
import com.umesh.unipay_1.dto.MyCardsResponse;
import com.umesh.unipay_1.entity.Card;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.exception.BusinessException;
import com.umesh.unipay_1.exception.ResourceNotFoundException;
import com.umesh.unipay_1.exception.UnauthorizedException;
import com.umesh.unipay_1.repository.CardRepository;
import com.umesh.unipay_1.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;

    /**
     * Links an existing card (by cardUid) to a user (by userId).
     *
     * Rules:
     * - The card must already exist in the system (cards are registered separately).
     * - The user must exist and be active & not blocked.
     * - A card can only be linked to one user at a time; re-linking to a different user
     *   requires calling unlink first OR this method re-assigns it (current behavior).
     */
    @Transactional
    public LinkCardResponse linkCard(LinkCardRequest request) {

        // 1. Find the card by UID
        Card card = cardRepository.findByCardUid(request.getCardUid())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Card not found with UID: " + request.getCardUid()));

        // 2. Ensure the card is active
        if (!card.isActive()) {
            throw new BusinessException("Card " + request.getCardUid() + " is inactive and cannot be linked");
        }

        // 3. Find the target user
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + request.getUserId()));

        // 4. Check user account state
        if (!user.isActive()) {
            throw new BusinessException("Cannot link card to a deactivated user account");
        }
        if (user.isBlocked()) {
            throw new BusinessException("Cannot link card to a blocked user account");
        }

        // 5. Warn if card is already linked to another user (re-assignment)
        if (card.getUser() != null && !card.getUser().getId().equals(user.getId())) {
            log.warn("Card {} is being re-linked from user {} to user {}",
                    card.getCardUid(), card.getUser().getId(), user.getId());
        }

        // 6. Perform the link
        LocalDateTime linkedAt = LocalDateTime.now();
        card.setUser(user);
        card.setLinkedAt(linkedAt);
        card = cardRepository.save(card);

        log.info("Card {} (id={}) successfully linked to user {} (id={})",
                card.getCardUid(), card.getId(), user.getUsername(), user.getId());

        // 7. Build and return the exact response format
        return LinkCardResponse.builder()
                .success(true)
                .message("Card " + card.getCardUid() + " linked to " + user.getUsername())
                .data(LinkCardResponse.CardData.builder()
                        .cardId(card.getId())
                        .cardUid(card.getCardUid())
                        .linkedTo(user.getUsername())
                        .linkedAt(linkedAt)
                        .build())
                .build();
    }

    /**
     * Registers a new card in the system (unassigned).
     * Cards must be registered before they can be linked to users.
     */
    @Transactional
    public LinkCardResponse.CardData registerCard(String cardUid) {
        if (cardRepository.existsByCardUid(cardUid)) {
            throw new BusinessException("Card with UID " + cardUid + " is already registered");
        }

        Card card = Card.builder()
                .cardUid(cardUid)
                .isActive(true)
                .build();
        card = cardRepository.save(card);

        log.info("New card registered: UID={}, id={}", card.getCardUid(), card.getId());

        return LinkCardResponse.CardData.builder()
                .cardId(card.getId())
                .cardUid(card.getCardUid())
                .linkedTo(null)
                .linkedAt(null)
                .build();
    }

    /**
     * Unlinks a card from its current user.
     */
    @Transactional
    public LinkCardResponse unlinkCard(String cardUid) {
        Card card = cardRepository.findByCardUid(cardUid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Card not found with UID: " + cardUid));

        if (card.getUser() == null) {
            throw new BusinessException("Card " + cardUid + " is not linked to any user");
        }

        String previousUserName = card.getUser().getUsername();
        card.setUser(null);
        card.setLinkedAt(null);
        cardRepository.save(card);

        log.info("Card {} unlinked from user {}", cardUid, previousUserName);

        return LinkCardResponse.builder()
                .success(true)
                .message("Card " + cardUid + " has been unlinked from " + previousUserName)
                .data(null)
                .build();
    }

    /**
     * ADMIN: Activate or deactivate any card by its UID.
     * No ownership check — ADMIN can toggle any card in the system.
     *
     * @param cardUid  the NFC/RFID card UID
     * @param activate true = activate, false = deactivate
     */
    @Transactional
    public CardStatusResponse setCardStatus(String cardUid, boolean activate) {
        Card card = cardRepository.findByCardUid(cardUid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Card not found with UID: " + cardUid));

        if (card.isActive() == activate) {
            throw new BusinessException(
                    "Card " + cardUid + " is already " + (activate ? "active" : "inactive"));
        }

        card.setActive(activate);
        cardRepository.save(card);

        String linkedTo = card.getUser() != null ? card.getUser().getUsername() : null;
        String action = activate ? "activated" : "deactivated";
        log.info("ADMIN {} card {} (linked to: {})", action, cardUid, linkedTo);

        return CardStatusResponse.builder()
                .success(true)
                .message("Card " + cardUid + " has been " + action + " successfully")
                .cardUid(card.getCardUid())
                .isActive(card.isActive())
                .isBlocked(card.isBlocked())
                .linkedTo(linkedTo)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * STUDENT: Activate or deactivate their own linked card.
     * Enforces ownership — a student can only toggle a card that is linked to them.
     *
     * @param cardUid  the NFC/RFID card UID
     * @param student  the authenticated user (must be the card owner)
     * @param activate true = activate, false = deactivate
     */
    @Transactional
    public CardStatusResponse setMyCardStatus(String cardUid, User student, boolean activate, String reason) {
        Card card = cardRepository.findByCardUid(cardUid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Card not found with UID: " + cardUid));


        // Ownership check — card must be linked to the authenticated student
        if (card.getUser() == null || !card.getUser().getId().equals(student.getId())) {
            throw new UnauthorizedException(
                    "You do not have permission to modify card " + cardUid);
        }

        if (card.isActive() == activate) {
            throw new BusinessException(
                    "Card " + cardUid + " is already " + (activate ? "active" : "inactive"));
        }
        card.setActive(activate);

        if (!activate) {
            card.setDeactivatedReason(reason);
            card.setDeactivatedByUserId(student.getId());
            card.setDeactivatedAt(LocalDateTime.now());
        } else {
            card.setDeactivatedReason(null);
            card.setDeactivatedByUserId(null);
            card.setDeactivatedAt(null);
        }

        cardRepository.save(card);

        String action = activate ? "activated" : "deactivated";
        log.info("STUDENT {} {} their card {}", student.getUsername(), action, cardUid);

        return CardStatusResponse.builder()
                .success(true)
                .message("Your card " + cardUid + " has been " + action + " successfully")
                .cardUid(card.getCardUid())
                .isActive(card.isActive())
                .isBlocked(card.isBlocked())
                .linkedTo(student.getUsername())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * STUDENT: Block their own card (lost/stolen).
     */
    @Transactional
    public CardStatusResponse blockMyCard(String cardUid, User student, String reason) {
        Card card = cardRepository.findByCardUid(cardUid)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with UID: " + cardUid));

        // Ownership check
        if (card.getUser() == null || !card.getUser().getId().equals(student.getId())) {
            throw new UnauthorizedException("You do not have permission to block card " + cardUid);
        }

        if (card.isBlocked()) {
            throw new BusinessException("Card " + cardUid + " is already blocked");
        }

        card.setBlocked(true);
        card.setBlockedReason(reason != null ? reason : "Blocked by student");
        cardRepository.save(card);

        log.info("STUDENT {} BLOCKED their card {}", student.getUsername(), cardUid);

        return CardStatusResponse.builder()
                .success(true)
                .message("Your card " + cardUid + " has been blocked successfully")
                .cardUid(card.getCardUid())
                .isActive(card.isActive())
                .isBlocked(card.isBlocked())
                .linkedTo(student.getUsername())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * STUDENT: Unblock their own card (found).
     */
    @Transactional
    public CardStatusResponse unblockMyCard(String cardUid, User student) {
        Card card = cardRepository.findByCardUid(cardUid)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with UID: " + cardUid));

        // Ownership check
        if (card.getUser() == null || !card.getUser().getId().equals(student.getId())) {
            throw new UnauthorizedException("You do not have permission to unblock card " + cardUid);
        }

        if (!card.isBlocked()) {
            throw new BusinessException("Card " + cardUid + " is not currently blocked");
        }

        card.setBlocked(false);
        card.setBlockedReason(null);
        cardRepository.save(card);

        log.info("STUDENT {} UNBLOCKED their card {}", student.getUsername(), cardUid);

        return CardStatusResponse.builder()
                .success(true)
                .message("Your card " + cardUid + " has been unblocked successfully")
                .cardUid(card.getCardUid())
                .isActive(card.isActive())
                .isBlocked(card.isBlocked())
                .linkedTo(student.getUsername())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Get all NFC cards linked to the specified user.
     *
     * @param user the authenticated User entity
     */
    public MyCardsResponse getMyCards(User user) {
        List<Card> cards = cardRepository.findAllByUserId(user.getId());

        List<MyCardsResponse.MyCardDto> dtos = cards.stream()
                .map(card -> MyCardsResponse.MyCardDto.builder()
                        .cardId(card.getId())
                        .cardUid(card.getCardUid())
                        .isBlocked(card.isBlocked())
                        .isActive(card.isActive())
                        .blockedReason(card.getBlockedReason())
                        .linkedAt(card.getLinkedAt())
                        .lastUsedAt(card.getLastUsedAt())
                        .build())
                .collect(Collectors.toList());

        return MyCardsResponse.builder()
                .success(true)
                .data(dtos)
                .build();
    }
}
