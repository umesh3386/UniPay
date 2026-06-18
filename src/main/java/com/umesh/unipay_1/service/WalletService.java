package com.umesh.unipay_1.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.umesh.unipay_1.dto.*;
import com.umesh.unipay_1.entity.RechargeOrder;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.entity.Wallet;
import com.umesh.unipay_1.entity.WalletTransaction;
import com.umesh.unipay_1.enums.RechargeOrderStatus;
import com.umesh.unipay_1.enums.TransactionType;
import com.umesh.unipay_1.exception.BusinessException;
import com.umesh.unipay_1.exception.ResourceNotFoundException;
import com.umesh.unipay_1.repository.RechargeOrderRepository;
import com.umesh.unipay_1.repository.WalletRepository;
import com.umesh.unipay_1.repository.WalletTransactionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final RechargeOrderRepository rechargeOrderRepository;

    @Value("${razorpay.api.key}")
    private String razorpayKeyId;

    @Value("${razorpay.api.secret}")
    private String razorpayKeySecret;

    private RazorpayClient razorpayClient;

    @PostConstruct
    public void init() {
        try {
            this.razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            log.info("Razorpay Client initialized successfully");
        } catch (RazorpayException e) {
            log.error("Failed to initialize Razorpay Client: {}", e.getMessage());
            // In production, you might want to fail fast if keys are misconfigured
        }
    }

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

    @Transactional
    public RechargeOrderResponse createRechargeOrder(User user, RechargeOrderRequest request) {
        // Business rule already checked by @Valid in controller, but enforce securely here
        if (request.getAmount().compareTo(new BigDecimal("10")) < 0 ||
            request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            throw new BusinessException("Amount must be between ₹10 and ₹10000");
        }

        try {
            // 1. Convert INR to paise for Razorpay API
            BigDecimal amountInPaise = request.getAmount().multiply(new BigDecimal("100"));
            long scaledAmount = amountInPaise.longValue();

            String receipt = "rcpt_" + user.getId() + "_" + System.currentTimeMillis();

            // 2. Call Razorpay API to create an order
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", scaledAmount);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", receipt);

            Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            String rzpOrderId = razorpayOrder.get("id");

            // 3. Save pending RechargeOrder intent in local DB
            RechargeOrder rechargeOrder = RechargeOrder.builder()
                    .user(user)
                    .razorpayOrderId(rzpOrderId)
                    .amount(request.getAmount())
                    .status(RechargeOrderStatus.PENDING)
                    .receiptId(receipt)
                    .build();
            rechargeOrderRepository.save(rechargeOrder);

            log.info("Recharge order {} created for user {}", rzpOrderId, user.getId());

            // 4. Return Data
            return RechargeOrderResponse.builder()
                    .success(true)
                    .data(RechargeOrderResponse.OrderData.builder()
                            .orderId(rzpOrderId)
                            .amount(scaledAmount)
                            .currency("INR")
                            .razorpayKeyId(razorpayKeyId)
                            .receipt(receipt)
                            .build())
                    .build();

        } catch (RazorpayException e) {
            log.error("Razorpay API Error during order creation: {}", e.getMessage());
            throw new BusinessException("Failed to initiate payment with payment gateway.");
        }
    }

    @Transactional
    public MessageResponse verifyRechargePayment(User user, VerifyPaymentRequest request) {
        // 1. Verify standard Razorpay Signature (Server-side validation)
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", request.getRazorpayOrderId());
            options.put("razorpay_payment_id", request.getRazorpayPaymentId());
            options.put("razorpay_signature", request.getRazorpaySignature());

            boolean isValidSignature = Utils.verifyPaymentSignature(options, razorpayKeySecret);
            if (!isValidSignature) {
                throw new BusinessException("Invalid payment signature detected. Verification failed.");
            }
        } catch (RazorpayException e) {
            log.error("Signature verification threw RazorpayException: {}", e.getMessage());
            throw new BusinessException("Failed to securely verify payment signature.");
        }

        // 2. Fetch the corresponding RechargeOrder
        RechargeOrder order = rechargeOrderRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Razorpay Order ID not found in system: " + request.getRazorpayOrderId()));

        // 3. Ensure order belongs to logged-in user
        if (!order.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Order does not belong to the authenticated user");
        }

        // 4. Idempotency Check: Don't process twice
        if (order.getStatus() == RechargeOrderStatus.SUCCESS) {
            return new MessageResponse("Payment historically verified and balance credited successfully");
        }
        if (order.getStatus() == RechargeOrderStatus.FAILED) {
            throw new BusinessException("This order is already marked as FAILED");
        }

        // 5. Success Flow: Update Order
        order.setStatus(RechargeOrderStatus.SUCCESS);
        order.setRazorpayPaymentId(request.getRazorpayPaymentId());
        order.setRazorpaySignature(request.getRazorpaySignature());
        rechargeOrderRepository.save(order);

        // 6. Credit the Wallet
        Wallet wallet = getOrCreateWallet(user);
        BigDecimal amountToAdd = order.getAmount();
        wallet.setBalance(wallet.getBalance().add(amountToAdd));
        // Hibernate throws OptimisticLockException here if version conflicts!
        walletRepository.save(wallet);

        // 7. Insert the Ledger Transaction
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .amount(amountToAdd)
                .direction(TransactionType.CREDIT)
                .transactionCategory("RECHARGE")
                .counterpartyName("Razorpay Gateway")
                .status("SUCCESS")
                .reference("Recharge Order: " + order.getRazorpayOrderId())
                .build();
        walletTransactionRepository.save(transaction);

        log.info("Wallet recharge verified for user {}. Added ₹{}. New balance: ₹{}",
                user.getId(), amountToAdd, wallet.getBalance());

        return new MessageResponse("Wallet recharged successfully with ₹" + amountToAdd);
    }
}
