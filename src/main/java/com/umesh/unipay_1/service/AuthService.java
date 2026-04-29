package com.umesh.unipay_1.service;

import org.springframework.beans.factory.annotation.Value;
import com.google.firebase.auth.UserRecord;
import com.umesh.unipay_1.dto.AuthResponse;
import com.umesh.unipay_1.dto.LoginRequest;
import com.umesh.unipay_1.dto.RegisterRequest;
import com.umesh.unipay_1.dto.UserResponse;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.enums.Role;
import com.umesh.unipay_1.exception.BusinessException;
import com.umesh.unipay_1.exception.ResourceNotFoundException;
import com.umesh.unipay_1.exception.UnauthorizedException;
import com.umesh.unipay_1.repository.UserRepository;
import com.umesh.unipay_1.repository.WalletRepository;
import com.umesh.unipay_1.entity.Wallet;
import com.umesh.unipay_1.util.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final FirebaseAuthService firebaseAuthService;
    private final JwtUtil jwtUtil;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public AuthResponse register(RegisterRequest request) {
        // 1. Check duplicates in DB first (before touching Firebase)
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered");
        }
        if (userRepository.existsByStudentId(request.getStudentId())) {
            throw new BusinessException("Student ID already registered");
        }

        // 2. Create user in Firebase
        UserRecord firebaseUser = firebaseAuthService.createFirebaseUser(
                request.getEmail(),
                request.getPassword(),
                request.getName()
        );

        // 3. Persist user in DB; roll back Firebase user on failure
        User user;
        try {
            user = User.builder()
                    .firebaseUid(firebaseUser.getUid())
                    .username(request.getName())
                    .email(request.getEmail())
                    .role(Role.STUDENT)
                    .studentId(request.getStudentId())
                    .isActive(true)
                    .isBlocked(false)
                    .build();
            user = userRepository.save(user);

            // Create an empty wallet for the user
            Wallet wallet = Wallet.builder().user(user).build();
            walletRepository.save(wallet);

        } catch (Exception dbException) {
            // Best-effort Firebase rollback
            log.error("DB save failed after Firebase user creation. Rolling back Firebase user {}: {}",
                    firebaseUser.getUid(), dbException.getMessage());
            try {
                firebaseAuthService.deleteFirebaseUser(firebaseUser.getUid());
                log.info("Successfully rolled back Firebase user {}", firebaseUser.getUid());
            } catch (Exception rollbackEx) {
                log.error("Failed to roll back Firebase user {} — manual cleanup required: {}",
                        firebaseUser.getUid(), rollbackEx.getMessage());
            }
            throw new BusinessException("Registration failed. Please try again.");
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 1. Verify credentials with Firebase REST API
        String firebaseUid = firebaseAuthService.signInWithEmailPassword(
                request.getEmail(),
                request.getPassword()
        );

        // 2. Load user from DB
        User user = userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found. Please register first."));

        // 3. Check if account is active
        if (!user.isActive()) {
            throw new UnauthorizedException("Your account has been deactivated");
        }

        // 4. Check if account is blocked
        if (user.isBlocked()) {
            throw new UnauthorizedException("Your account has been blocked. Please contact support.");
        }

        // 5. Issue tokens
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse merchantLogin(LoginRequest request) {
        // 1. Verify credentials with Firebase REST API
        String firebaseUid = firebaseAuthService.signInWithEmailPassword(
                request.getEmail(),
                request.getPassword()
        );

        // 2. Load user from DB
        User user = userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found. Please contact your administrator."));

        // 3. Enforce MERCHANT role — prevent students from logging in here
        if (user.getRole() != Role.MERCHANT) {
            throw new UnauthorizedException("Access denied. This login is for merchants only.");
        }

        // 4. Check if account is active
        if (!user.isActive()) {
            throw new UnauthorizedException("Your account has been deactivated");
        }

        // 5. Check if account is blocked
        if (user.isBlocked()) {
            throw new UnauthorizedException("Your account has been blocked. Please contact support.");
        }

        // 6. Issue tokens
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        // 1. Validate JWT signature and expiry
        jwtUtil.validateTokenAndGetClaims(refreshToken);

        // 2. Find user by stored refresh token (DB lookup prevents token reuse after logout)
        User user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid or already-used refresh token"));

        // 3. Check account is still active
        if (!user.isActive()) {
            throw new UnauthorizedException("Your account has been deactivated");
        }

        // 4. Check account is not blocked
        if (user.isBlocked()) {
            throw new UnauthorizedException("Your account has been blocked. Please contact support.");
        }

        // 5. Rotate token pair (old refresh token is replaced)
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(User user) {
        if (user.getRefreshToken() != null) {
            user.setRefreshToken(null);
            userRepository.save(user);
            log.info("User {} ({}) logged out successfully", user.getId(), user.getEmail());
        } else {
            log.warn("Logout called for user {} but no active session found", user.getId());
        }
    }

    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.refreshAccessToken(user);

        // Store refresh token in DB
        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expirationMs / 1000)
                .user(mapToUserResponse(user))
                .build();
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .studentId(user.getStudentId())
                .businessName(user.getBusinessName())
                .isActive(user.isActive())
                .isBlocked(user.isBlocked())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
