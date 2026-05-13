package com.umesh.unipay_1.service;

import com.umesh.unipay_1.dto.PaymentStatusResponse;
import com.umesh.unipay_1.dto.PaymentTapRequest;
import com.umesh.unipay_1.dto.PaymentTapResponse;
import com.umesh.unipay_1.dto.RefundRequest;
import com.umesh.unipay_1.dto.RefundResponse;
import com.umesh.unipay_1.entity.Card;
import com.umesh.unipay_1.entity.Payment;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.entity.Wallet;
import com.umesh.unipay_1.entity.WalletTransaction;
import com.umesh.unipay_1.enums.TransactionType;
import com.umesh.unipay_1.exception.BusinessException;
import com.umesh.unipay_1.exception.CardBlockedException;
import com.umesh.unipay_1.exception.MerchantMismatchException;

import com.umesh.unipay_1.exception.PaymentRequiredException;
import com.umesh.unipay_1.exception.ResourceNotFoundException;


import com.umesh.unipay_1.exception.UnauthorizedException;
import com.umesh.unipay_1.repository.CardRepository;
import com.umesh.unipay_1.repository.PaymentRepository;
import com.umesh.unipay_1.repository.WalletRepository;
import com.umesh.unipay_1.repository.WalletTransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final CardRepository cardRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PaymentRepository paymentRepository;

    @org.springframework.beans.factory.annotation.Value("${app.payment.tap.min-amount:1.00}")
    private BigDecimal minTapAmount;

    @org.springframework.beans.factory.annotation.Value("${app.payment.tap.max-amount:5000.00}")
    private BigDecimal maxTapAmount;


    @Transactional
    public PaymentTapResponse processNfcPayment(User merchantParam, PaymentTapRequest request) {
        
        // 1. Merchant Mismatch Verification
        if (!Objects.equals(merchantParam.getId(), request.getMerchantId())) {
            throw new MerchantMismatchException("Merchant ID does not match authenticated user");
        }

        // 1.1 Amount Boundary Verification
        if (request.getAmount().compareTo(minTapAmount) < 0 || request.getAmount().compareTo(maxTapAmount) > 0) {
            throw new BusinessException("Transaction amount must be between ₹" + minTapAmount + " and ₹" + maxTapAmount);
        }


        // 2. Locate Card

        Card card = cardRepository.findByCardUid(request.getCardUid())
                .orElseThrow(() -> new ResourceNotFoundException("Card UID not registered in the system"));

        // 3. Card status evaluations
        if (card.getUser() == null) {
            throw new UnauthorizedException("Card is currently unassigned");
        }
        if (!card.isActive() || card.isBlocked()) {
            throw new CardBlockedException("This card is currently disabled or blocked and cannot be used for payments.");
        }


        User student = card.getUser();

        // 4. Lock Student Wallet (PESSIMISTIC_WRITE)
        Wallet studentWallet = walletRepository.findByUserIdForUpdate(student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student wallet not found"));

        if (studentWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new PaymentRequiredException("Insufficient balance in user wallet");
        }

        // 5. Lock Merchant Wallet (PESSIMISTIC_WRITE)
        // Note: Ordering is strictly Student -> Merchant to prevent deadlocks
        Wallet merchantWallet = walletRepository.findByUserIdForUpdate(merchantParam.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant wallet not found"));

        // 6. Perform the transfer
        BigDecimal transferAmount = request.getAmount();

        studentWallet.setBalance(studentWallet.getBalance().subtract(transferAmount));
        merchantWallet.setBalance(merchantWallet.getBalance().add(transferAmount));

        walletRepository.save(studentWallet);
        walletRepository.save(merchantWallet);

        // 7. Core Payment Event Table
        Payment payment = Payment.builder()
                .student(student)
                .merchant(merchantParam)
                .amount(transferAmount)
                .status("SUCCESS")
                .type("NFC_PAYMENT")
                .build();
        payment = paymentRepository.save(payment);

        // 8. Audit Ledger Logging
        String merchantDisplayName = merchantParam.getBusinessName() != null ? merchantParam.getBusinessName() : merchantParam.getUsername();
        
        WalletTransaction studentDebit = WalletTransaction.builder()
                .wallet(studentWallet)
                .amount(transferAmount)
                .direction(TransactionType.DEBIT)
                .transactionCategory("NFC_PAYMENT")
                .counterpartyName(merchantDisplayName)
                .cardUid(request.getCardUid())
                .status("SUCCESS")
                .reference("Payment: " + payment.getId())
                .build();

        WalletTransaction merchantCredit = WalletTransaction.builder()
                .wallet(merchantWallet)
                .amount(transferAmount)
                .direction(TransactionType.CREDIT)
                .transactionCategory("NFC_PAYMENT")
                .counterpartyName(student.getUsername())
                .cardUid(request.getCardUid())
                .status("SUCCESS")
                .reference("Payment: " + payment.getId())
                .build();

        walletTransactionRepository.save(studentDebit);
        walletTransactionRepository.save(merchantCredit);

        // 9. Update last used timestamp for card
        card.setLastUsedAt(LocalDateTime.now());
        cardRepository.save(card);

        log.info("Payment of {} strictly executed from {} to merchant {}", transferAmount, student.getId(), merchantParam.getId());

        // 10. Construct successful response payload
        return PaymentTapResponse.builder()
                .success(true)
                .message("Payment successful")
                .data(PaymentTapResponse.PaymentData.builder()
                        .transactionId(payment.getId())
                        .amount(transferAmount)
                        .userBalance(studentWallet.getBalance())
                        .userName(student.getUsername())
                        .merchantName(merchantParam.getBusinessName() != null ? merchantParam.getBusinessName() : merchantParam.getUsername())
                        .timestamp(payment.getCreatedAt() != null ? payment.getCreatedAt() : LocalDateTime.now())
                        .build())
                .build();
    }

    public PaymentStatusResponse getPaymentStatus(Long id, User requester) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        // Authorization check - only involved parties can view
        if (!Objects.equals(payment.getStudent().getId(), requester.getId()) &&
            !Objects.equals(payment.getMerchant().getId(), requester.getId())) {
            throw new UnauthorizedException("You do not have permission to view this transaction");
        }

        User merchant = payment.getMerchant();
        String merchantDisplay = merchant.getBusinessName() != null ? merchant.getBusinessName() : merchant.getUsername();

        return PaymentStatusResponse.builder()
                .success(true)
                .data(PaymentStatusResponse.PaymentDetails.builder()
                        .transactionId(payment.getId())
                        .status(payment.getStatus())
                        .amount(payment.getAmount())
                        .type(payment.getType())
                        .merchantName(merchantDisplay)
                        .createdAt(payment.getCreatedAt())
                        .build())
                .build();
    }

    @Transactional
    public RefundResponse processRefund(User merchantParam, RefundRequest request) {

        // 1. Locate the precise original payment
        Payment payment = paymentRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (!Objects.equals(payment.getMerchant().getId(), merchantParam.getId())) {
            throw new MerchantMismatchException("Merchant ID does not match authenticated user");
        }

        // 2. State & Amount Validations

        if ("REFUNDED".equals(payment.getStatus())) {
            throw new BusinessException("This transaction has already been fully refunded.");
        }
        
        BigDecimal refundAmount = request.getAmount();
        BigDecimal availableToRefund = payment.getAmount().subtract(payment.getRefundedAmount());

        if (refundAmount.compareTo(availableToRefund) > 0) {
            throw new BusinessException("Requested refund amount exceeds the remaining unrefunded transaction value (Max available: " + availableToRefund + ")");
        }

        User student = payment.getStudent();

        // 3. PostgreSQL Pessimistic Locks - STRICT ORDER: Student -> Merchant to avert deadlocks natively!
        Wallet studentWallet = walletRepository.findByUserIdForUpdate(student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student wallet not found across boundary"));

        Wallet merchantWallet = walletRepository.findByUserIdForUpdate(merchantParam.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant wallet not found"));

        // 4. Merchant Liquid Assessment
        if (merchantWallet.getBalance().compareTo(refundAmount) < 0) {
            throw new PaymentRequiredException("Insufficient merchant wallet balance to honor this refund request");
        }

        // 5. Transfer Funds Reverse
        merchantWallet.setBalance(merchantWallet.getBalance().subtract(refundAmount));
        studentWallet.setBalance(studentWallet.getBalance().add(refundAmount));

        walletRepository.save(studentWallet);
        walletRepository.save(merchantWallet);

        // 6. Ledger Audit Trail for specific refund injection
        WalletTransaction merchantDebit = WalletTransaction.builder()
                .wallet(merchantWallet)
                .amount(refundAmount)
                .direction(TransactionType.DEBIT)
                .transactionCategory("REFUND")
                .counterpartyName(student.getUsername())
                .status("SUCCESS")
                .reference("Refund for Payment: " + payment.getId())
                .build();

        WalletTransaction studentCredit = WalletTransaction.builder()
                .wallet(studentWallet)
                .amount(refundAmount)
                .direction(TransactionType.CREDIT)
                .transactionCategory("REFUND")
                .counterpartyName(merchantParam.getBusinessName() != null ? merchantParam.getBusinessName() : merchantParam.getUsername())
                .status("SUCCESS")
                .reference("Refund for Payment: " + payment.getId())
                .build();

        // Returning the studentCredit gives us a unique ledger ID to map this specific sub-refund
        studentCredit = walletTransactionRepository.save(studentCredit);
        walletTransactionRepository.save(merchantDebit);

        // 7. Update original Payment Status tracking logic
        payment.setRefundedAmount(payment.getRefundedAmount().add(refundAmount));
        
        if (payment.getRefundedAmount().compareTo(payment.getAmount()) == 0) {
            payment.setStatus("REFUNDED");
        } else {
            payment.setStatus("PARTIALLY_REFUNDED");
        }
        paymentRepository.save(payment);

        log.info("Partial custom refund of {} executed cleanly to {} originating against payment {}", refundAmount, student.getId(), payment.getId());

        // 8. Yield Response Engine
        return RefundResponse.builder()
                .success(true)
                .message("Refund processed successfully")
                .data(RefundResponse.RefundData.builder()
                        .refundTransactionId(studentCredit.getId())
                        .originalTransactionId(payment.getId())
                        .refundedAmount(refundAmount)
                        .remainingAmount(payment.getAmount().subtract(payment.getRefundedAmount()))
                        .merchantBalance(merchantWallet.getBalance().toString())
                        .refundedAt(LocalDateTime.now())
                        .build())
                .build();
    }
}

