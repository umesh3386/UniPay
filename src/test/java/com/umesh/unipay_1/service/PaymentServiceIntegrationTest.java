package com.umesh.unipay_1.service;

import com.google.firebase.FirebaseApp;
import com.umesh.unipay_1.dto.PaymentTapRequest;
import com.umesh.unipay_1.dto.PaymentTapResponse;
import com.umesh.unipay_1.dto.RefundRequest;
import com.umesh.unipay_1.dto.RefundResponse;
import com.umesh.unipay_1.entity.Card;
import com.umesh.unipay_1.entity.Payment;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.entity.Wallet;
import com.umesh.unipay_1.entity.WalletTransaction;
import com.umesh.unipay_1.enums.Role;
import com.umesh.unipay_1.enums.TransactionType;
import com.umesh.unipay_1.exception.BusinessException;
import com.umesh.unipay_1.exception.PaymentRequiredException;
import com.umesh.unipay_1.repository.CardRepository;
import com.umesh.unipay_1.repository.PaymentRepository;
import com.umesh.unipay_1.repository.UserRepository;
import com.umesh.unipay_1.repository.WalletRepository;
import com.umesh.unipay_1.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceIntegrationTest {

    static PostgreSQLContainer<?> postgres;

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
    }

    @MockitoBean
    private FirebaseApp firebaseApp;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @BeforeEach
    void setUp() {
        walletTransactionRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        cardRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User createTestUser(String username, String email, Role role, String firebaseUid, String businessName) {
        User user = User.builder()
                .username(username)
                .email(email)
                .role(role)
                .firebaseUid(firebaseUid)
                .businessName(businessName)
                .isActive(true)
                .isBlocked(false)
                .build();
        return userRepository.save(user);
    }

    private Wallet createWallet(User user, BigDecimal balance) {
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(balance)
                .build();
        return walletRepository.save(wallet);
    }

    private Card createCard(String cardUid, User user) {
        Card card = Card.builder()
                .cardUid(cardUid)
                .user(user)
                .isActive(true)
                .isBlocked(false)
                .build();
        return cardRepository.save(card);
    }

    @Test
    @DisplayName("Happy Path: NFC tap payment transfers funds and creates ledger entries")
    void testHappyPathNfcPayment() {
        // Arrange
        User student = createTestUser("student1", "student1@uni.edu", Role.STUDENT, "fuid_s1", null);
        Wallet studentWallet = createWallet(student, new BigDecimal("500.00"));

        User merchant = createTestUser("merchant1", "merchant1@uni.edu", Role.MERCHANT, "fuid_m1", "Campus Cafe");
        Wallet merchantWallet = createWallet(merchant, BigDecimal.ZERO);

        createCard("CARD_HAPPY_123", student);

        PaymentTapRequest request = new PaymentTapRequest();
        request.setCardUid("CARD_HAPPY_123");
        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantId(merchant.getId());

        // Act
        PaymentTapResponse response = paymentService.processNfcPayment(merchant, request);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());

        Wallet updatedStudentWallet = walletRepository.findByUserId(student.getId()).orElseThrow();
        Wallet updatedMerchantWallet = walletRepository.findByUserId(merchant.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("400.00").compareTo(updatedStudentWallet.getBalance()));
        assertEquals(0, new BigDecimal("100.00").compareTo(updatedMerchantWallet.getBalance()));

        List<Payment> payments = paymentRepository.findAll();
        assertEquals(1, payments.size());
        Payment payment = payments.get(0);
        assertEquals("SUCCESS", payment.getStatus());
        assertEquals("NFC_PAYMENT", payment.getType());
        assertEquals(0, new BigDecimal("100.00").compareTo(payment.getAmount()));

        List<WalletTransaction> transactions = walletTransactionRepository.findAll();
        assertEquals(2, transactions.size());

        boolean hasDebit = transactions.stream().anyMatch(t ->
                t.getDirection() == TransactionType.DEBIT
                        && t.getWallet().getId().equals(studentWallet.getId())
                        && t.getAmount().compareTo(new BigDecimal("100.00")) == 0);
        boolean hasCredit = transactions.stream().anyMatch(t ->
                t.getDirection() == TransactionType.CREDIT
                        && t.getWallet().getId().equals(merchantWallet.getId())
                        && t.getAmount().compareTo(new BigDecimal("100.00")) == 0);

        assertTrue(hasDebit, "Student wallet must have a DEBIT ledger transaction");
        assertTrue(hasCredit, "Merchant wallet must have a CREDIT ledger transaction");
    }

    @Test
    @DisplayName("Insufficient balance: Payment throws PaymentRequiredException and balances remain unchanged")
    void testInsufficientBalancePayment() {
        // Arrange
        User student = createTestUser("student2", "student2@uni.edu", Role.STUDENT, "fuid_s2", null);
        createWallet(student, new BigDecimal("50.00"));

        User merchant = createTestUser("merchant2", "merchant2@uni.edu", Role.MERCHANT, "fuid_m2", "Campus Cafe");
        createWallet(merchant, BigDecimal.ZERO);

        createCard("CARD_LOW_BAL", student);

        PaymentTapRequest request = new PaymentTapRequest();
        request.setCardUid("CARD_LOW_BAL");
        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantId(merchant.getId());

        // Act & Assert
        assertThrows(PaymentRequiredException.class, () -> paymentService.processNfcPayment(merchant, request));

        Wallet updatedStudentWallet = walletRepository.findByUserId(student.getId()).orElseThrow();
        Wallet updatedMerchantWallet = walletRepository.findByUserId(merchant.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("50.00").compareTo(updatedStudentWallet.getBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(updatedMerchantWallet.getBalance()));
        assertEquals(0, paymentRepository.count());
        assertEquals(0, walletTransactionRepository.count());
    }

    @Test
    @DisplayName("Concurrency: Two concurrent payments against balance of 100 allow exactly one to succeed")
    void testConcurrentNfcPayments_pessimisticLocking() throws InterruptedException {
        // Arrange: student has ₹100, both threads want to pay ₹100
        User student = createTestUser("student3", "student3@uni.edu", Role.STUDENT, "fuid_s3", null);
        createWallet(student, new BigDecimal("100.00"));

        User merchant = createTestUser("merchant3", "merchant3@uni.edu", Role.MERCHANT, "fuid_s3_m", "Campus Store");
        createWallet(merchant, BigDecimal.ZERO);

        createCard("CARD_CONCURRENT", student);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    PaymentTapRequest request = new PaymentTapRequest();
                    request.setCardUid("CARD_CONCURRENT");
                    request.setAmount(new BigDecimal("100.00"));
                    request.setMerchantId(merchant.getId());

                    paymentService.processNfcPayment(merchant, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start both threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrent payment threads should complete within timeout");
        assertEquals(1, successCount.get(), "Exactly one payment must succeed");
        assertEquals(1, failureCount.get(), "Exactly one payment must fail due to insufficient funds");

        Wallet finalStudentWallet = walletRepository.findByUserId(student.getId()).orElseThrow();
        Wallet finalMerchantWallet = walletRepository.findByUserId(merchant.getId()).orElseThrow();

        assertEquals(0, BigDecimal.ZERO.compareTo(finalStudentWallet.getBalance()), "Student balance must be exactly 0.00");
        assertEquals(0, new BigDecimal("100.00").compareTo(finalMerchantWallet.getBalance()), "Merchant balance must be exactly 100.00");
    }

    @Test
    @DisplayName("Refund: Partial refund succeeds and over-refund is rejected")
    void testRefundHappyPathAndOverRefund() {
        // Arrange: student pays ₹100 to merchant
        User student = createTestUser("student4", "student4@uni.edu", Role.STUDENT, "fuid_s4", null);
        createWallet(student, new BigDecimal("500.00"));

        User merchant = createTestUser("merchant4", "merchant4@uni.edu", Role.MERCHANT, "fuid_s4_m", "Campus Bookshop");
        createWallet(merchant, BigDecimal.ZERO);

        createCard("CARD_REFUND_123", student);

        PaymentTapRequest tapRequest = new PaymentTapRequest();
        tapRequest.setCardUid("CARD_REFUND_123");
        tapRequest.setAmount(new BigDecimal("100.00"));
        tapRequest.setMerchantId(merchant.getId());

        PaymentTapResponse tapResponse = paymentService.processNfcPayment(merchant, tapRequest);
        assertNotNull(tapResponse);

        Payment initialPayment = paymentRepository.findAll().get(0);

        // Act 1: Partial refund of ₹40.00
        RefundRequest refundReq = new RefundRequest();
        refundReq.setTransactionId(initialPayment.getId());
        refundReq.setAmount(new BigDecimal("40.00"));

        RefundResponse refundResponse = paymentService.processRefund(merchant, refundReq);

        // Assert 1: Partial refund results
        assertNotNull(refundResponse);
        assertTrue(refundResponse.isSuccess());

        Payment partiallyRefundedPayment = paymentRepository.findById(initialPayment.getId()).orElseThrow();
        assertEquals("PARTIALLY_REFUNDED", partiallyRefundedPayment.getStatus());
        assertEquals(0, new BigDecimal("40.00").compareTo(partiallyRefundedPayment.getRefundedAmount()));

        Wallet studentWalletAfterRefund = walletRepository.findByUserId(student.getId()).orElseThrow();
        Wallet merchantWalletAfterRefund = walletRepository.findByUserId(merchant.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("440.00").compareTo(studentWalletAfterRefund.getBalance()));
        assertEquals(0, new BigDecimal("60.00").compareTo(merchantWalletAfterRefund.getBalance()));

        // Act 2: Over-refund attempt of ₹70.00 (only ₹60.00 remaining refundable)
        RefundRequest overRefundReq = new RefundRequest();
        overRefundReq.setTransactionId(initialPayment.getId());
        overRefundReq.setAmount(new BigDecimal("70.00"));

        // Assert 2: Over-refund rejected with BusinessException and state untouched
        assertThrows(BusinessException.class, () -> paymentService.processRefund(merchant, overRefundReq));

        Wallet finalStudentWallet = walletRepository.findByUserId(student.getId()).orElseThrow();
        Wallet finalMerchantWallet = walletRepository.findByUserId(merchant.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("440.00").compareTo(finalStudentWallet.getBalance()));
        assertEquals(0, new BigDecimal("60.00").compareTo(finalMerchantWallet.getBalance()));
    }
}
