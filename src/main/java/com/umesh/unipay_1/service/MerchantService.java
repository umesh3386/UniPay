package com.umesh.unipay_1.service;

import com.google.firebase.auth.UserRecord;
import com.umesh.unipay_1.dto.AuthResponse;
import com.umesh.unipay_1.dto.MerchantProfileResponse;
import com.umesh.unipay_1.dto.RegisterMerchantRequest;
import com.umesh.unipay_1.dto.UserResponse;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.enums.Role;
import com.umesh.unipay_1.exception.BusinessException;
import com.umesh.unipay_1.repository.PaymentRepository;
import com.umesh.unipay_1.repository.UserRepository;
import com.umesh.unipay_1.repository.WalletRepository;
import com.umesh.unipay_1.entity.Wallet;
import java.math.BigDecimal;

import com.umesh.unipay_1.util.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MerchantService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PaymentRepository paymentRepository;
    private final FirebaseAuthService firebaseAuthService;
    private final JwtUtil jwtUtil;


    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Register a new MERCHANT account.
     * This method is intended to be called by ADMIN only (enforced at controller level).
     */
    public UserResponse registerMerchant(RegisterMerchantRequest request) {
        // 1. Check email uniqueness in DB
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered");
        }

        // 2. Create Firebase user
        UserRecord firebaseUser = firebaseAuthService.createFirebaseUser(
                request.getEmail(),
                request.getPassword(),
                request.getName()
        );

        // 3. Persist merchant in DB; roll back Firebase on failure
        User merchant;
        try {
            merchant = User.builder()
                    .firebaseUid(firebaseUser.getUid())
                    .username(request.getName())
                    .email(request.getEmail())
                    .role(Role.MERCHANT)
                    .businessName(request.getBusinessName())
                    .isActive(true)
                    .isBlocked(false)
                    .build();
            merchant = userRepository.save(merchant);

            // Initialize an empty wallet for the merchant
            Wallet wallet = Wallet.builder().user(merchant).build();
            walletRepository.save(wallet);

        } catch (Exception dbException) {
            log.error("DB save failed after Firebase merchant creation. Rolling back Firebase user {}: {}",
                    firebaseUser.getUid(), dbException.getMessage());
            try {
                firebaseAuthService.deleteFirebaseUser(firebaseUser.getUid());
                log.info("Successfully rolled back Firebase user {}", firebaseUser.getUid());
            } catch (Exception rollbackEx) {
                log.error("Failed to roll back Firebase user {} — manual cleanup required: {}",
                        firebaseUser.getUid(), rollbackEx.getMessage());
            }
            throw new BusinessException("Merchant registration failed. Please try again.");
        }

        log.info("ADMIN registered new merchant: id={}, email={}", merchant.getId(), merchant.getEmail());
        return mapToUserResponse(merchant);
    }

    /**
     * List all registered merchants (ADMIN view).
     */
    public List<UserResponse> getAllMerchants() {
        return userRepository.findAllByRole(Role.MERCHANT)
                .stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    public MerchantProfileResponse getMerchantDashboardProfile(User user) {
        BigDecimal totalRcv = paymentRepository.sumTotalReceivedByMerchantId(user.getId());
        if (totalRcv == null) {
            totalRcv = BigDecimal.ZERO;
        }

        return MerchantProfileResponse.builder()
                .success(true)
                .data(MerchantProfileResponse.MerchantData.builder()
                        .businessName(user.getBusinessName())
                        .totalReceived(totalRcv)
                        .isActive(user.isActive() && !user.isBlocked())
                        .build())
                .build();
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .businessName(user.getBusinessName())
                .isActive(user.isActive())
                .isBlocked(user.isBlocked())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
