package com.umesh.unipay_1.service;


import com.umesh.unipay_1.dto.UpdateProfileRequest;

import com.umesh.unipay_1.dto.UserListResponse;
import com.umesh.unipay_1.dto.UserProfileResponse;
import com.umesh.unipay_1.dto.UserResponse;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.entity.Wallet;
import com.umesh.unipay_1.enums.Role;
import com.umesh.unipay_1.exception.BusinessException;
import com.umesh.unipay_1.exception.ResourceNotFoundException;
import com.umesh.unipay_1.repository.CardRepository;

import com.umesh.unipay_1.repository.UserRepository;
import com.umesh.unipay_1.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CardRepository cardRepository;



    public UserProfileResponse getProfile(User user) {
        BigDecimal balance = walletRepository.findByUserId(user.getId())
                .map(Wallet::getBalance)
                .orElse(BigDecimal.ZERO);

        int linkedCards = cardRepository.findAllByUserId(user.getId()).size();

        return UserProfileResponse.builder()
                .success(true)
                .data(UserProfileResponse.UserProfileData.builder()
                        .userId(user.getId())
                        .fullName(user.getUsername())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .role(user.getRole().name())
                        .isActive(user.isActive())
                        .walletBalance(balance)
                        .linkedCards(linkedCards)
                        .createdAt(user.getCreatedAt())
                        .build())
                .build();
    }

    public UserProfileResponse updateProfile(User user, UpdateProfileRequest request) {
        boolean isUpdated = false;

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setUsername(request.getFullName().trim());
            isUpdated = true;
        }

        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            user.setPhone(request.getPhone().trim());
            isUpdated = true;
        }

        if (!isUpdated) {
            throw new BusinessException("No valid fields provided to update");
        }

        userRepository.save(user);

        // Return the fresh profile
        return getProfile(user);
    }

    public UserListResponse getAllUsersPaginated(Role role, int page, int size) {
        PageRequest pr = PageRequest.of(page, size);
        Page<User> usersPage;

        if (role != null) {
            usersPage = userRepository.findAllByRole(role, pr);
        } else {
            usersPage = userRepository.findAll(pr);
        }

        List<UserResponse> dtos = usersPage.getContent().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());


        return UserListResponse.builder()
                .success(true)
                .data(UserListResponse.UserPageData.builder()
                        .users(dtos)
                        .currentPage(usersPage.getNumber())
                        .totalElements(usersPage.getTotalElements())
                        .totalPages(usersPage.getTotalPages())
                        .build())
                .build();
    }

    public UserResponse blockUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        user.setBlocked(true);
        user.setBlockedReason(reason);
        user = userRepository.save(user);

        return mapToUserResponse(user);
    }

    public UserResponse unblockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setBlocked(false);
        user.setBlockedReason(null);
        user = userRepository.save(user);

        return mapToUserResponse(user);
    }

    public UserProfileResponse getUserDetails(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return getProfile(user);
    }

    private UserResponse mapToUserResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .name(u.getUsername())
                .studentId(u.getStudentId())
                .businessName(u.getBusinessName())
                .email(u.getEmail())
                .role(u.getRole().name())
                .isActive(u.isActive())
                .isBlocked(u.isBlocked())
                .createdAt(u.getCreatedAt())
                .build();
    }

}


