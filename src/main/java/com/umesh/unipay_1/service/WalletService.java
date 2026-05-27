package com.umesh.unipay_1.service;

import com.umesh.unipay_1.dto.*;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.entity.Wallet;
import com.umesh.unipay_1.entity.WalletTransaction;
import com.umesh.unipay_1.enums.TransactionType;
import com.umesh.unipay_1.repository.WalletRepository;
import com.umesh.unipay_1.repository.WalletTransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    /**
     * Retrieves the user's wallet. If it doesn't exist (e.g. older users), creates it.
     */
    @Transactional
    public Wallet getOrCreateWallet(User user) {
        return walletRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    log.info("Creating new wallet for user {}", user.getId());
                    Wallet newWallet = Wallet.builder().user(user).build();
                    return walletRepository.save(newWallet);
                });
    }

    public WalletBalanceResponse getBalance(User user) {
        Wallet wallet = getOrCreateWallet(user);
        return WalletBalanceResponse.builder()
                .success(true)
                .message("Balance retrieved securely")
                .balance(wallet.getBalance())
                .build();
    }

}
