package com.umesh.unipay_1.service;

import com.google.firebase.FirebaseApp;
import com.razorpay.Utils;
import com.umesh.unipay_1.dto.MessageResponse;
import com.umesh.unipay_1.dto.VerifyPaymentRequest;
import com.umesh.unipay_1.entity.RechargeOrder;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.entity.Wallet;
import com.umesh.unipay_1.entity.WalletTransaction;
import com.umesh.unipay_1.enums.RechargeOrderStatus;
import com.umesh.unipay_1.enums.Role;
import com.umesh.unipay_1.enums.TransactionType;
import com.umesh.unipay_1.repository.RechargeOrderRepository;
import com.umesh.unipay_1.repository.UserRepository;
import com.umesh.unipay_1.repository.WalletRepository;
import com.umesh.unipay_1.repository.WalletTransactionRepository;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
@ActiveProfiles("test")
class WalletServiceIntegrationTest {

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
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RechargeOrderRepository rechargeOrderRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @BeforeEach
    void setUp() {
        walletTransactionRepository.deleteAllInBatch();
        rechargeOrderRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User createTestUser(String username, String email, Role role, String firebaseUid) {
        User user = User.builder()
                .username(username)
                .email(email)
                .role(role)
                .firebaseUid(firebaseUid)
                .isActive(true)
                .isBlocked(false)
                .build();
        return userRepository.save(user);
    }

    @Test
    @DisplayName("Idempotency: Verifying same recharge payment twice credits wallet only once")
    void testVerifyRechargePayment_idempotent() {
        // Arrange
        User user = createTestUser("recharge_user", "recharge@uni.edu", Role.STUDENT, "fuid_recharge_1");
        
        RechargeOrder order = RechargeOrder.builder()
                .user(user)
                .razorpayOrderId("order_test_idempotent_123")
                .amount(new BigDecimal("200.00"))
                .status(RechargeOrderStatus.PENDING)
                .receiptId("receipt_123")
                .build();
        rechargeOrderRepository.save(order);

        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setRazorpayOrderId("order_test_idempotent_123");
        request.setRazorpayPaymentId("pay_test_idempotent_123");
        request.setRazorpaySignature("sig_test_idempotent_123");

        try (MockedStatic<Utils> mockedUtils = Mockito.mockStatic(Utils.class)) {
            mockedUtils.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), anyString()))
                    .thenReturn(true);

            // Act 1: First verification - should process and credit wallet
            MessageResponse firstResponse = walletService.verifyRechargePayment(user, request);

            // Assert 1
            assertNotNull(firstResponse);
            assertTrue(firstResponse.getMessage().contains("Wallet recharged successfully"));

            RechargeOrder orderAfterFirst = rechargeOrderRepository.findByRazorpayOrderId("order_test_idempotent_123").orElseThrow();
            assertEquals(RechargeOrderStatus.SUCCESS, orderAfterFirst.getStatus());
            assertEquals("pay_test_idempotent_123", orderAfterFirst.getRazorpayPaymentId());

            Wallet walletAfterFirst = walletRepository.findByUserId(user.getId()).orElseThrow();
            assertEquals(0, new BigDecimal("200.00").compareTo(walletAfterFirst.getBalance()), "Wallet balance should be 200.00 after first recharge");

            List<WalletTransaction> txsAfterFirst = walletTransactionRepository.findAll();
            assertEquals(1, txsAfterFirst.size());
            assertEquals(TransactionType.CREDIT, txsAfterFirst.get(0).getDirection());
            assertEquals(0, new BigDecimal("200.00").compareTo(txsAfterFirst.get(0).getAmount()));

            // Act 2: Second verification with identical request - must be idempotent (no double credit)
            MessageResponse secondResponse = walletService.verifyRechargePayment(user, request);

            // Assert 2
            assertNotNull(secondResponse);
            assertTrue(secondResponse.getMessage().contains("historically verified"), 
                    "Second verification should return historical verification message");

            Wallet walletAfterSecond = walletRepository.findByUserId(user.getId()).orElseThrow();
            assertEquals(0, new BigDecimal("200.00").compareTo(walletAfterSecond.getBalance()), 
                    "Wallet balance must STILL be 200.00 (no double credit)");

            List<WalletTransaction> txsAfterSecond = walletTransactionRepository.findAll();
            assertEquals(1, txsAfterSecond.size(), "No additional ledger transaction must be created");
        }
    }
}
