package com.umesh.unipay_1.service;

import com.umesh.unipay_1.dto.TransactionDetailResponse;
import com.umesh.unipay_1.dto.TransactionHistoryResponse;
import com.umesh.unipay_1.entity.Payment;
import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.entity.WalletTransaction;
import com.umesh.unipay_1.enums.TransactionType;
import com.umesh.unipay_1.exception.ResourceNotFoundException;
import com.umesh.unipay_1.exception.UnauthorizedException;
import com.umesh.unipay_1.repository.PaymentRepository;
import com.umesh.unipay_1.repository.WalletTransactionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final WalletTransactionRepository walletTransactionRepository;
    private final PaymentRepository paymentRepository;

    public TransactionHistoryResponse getTransactionHistory(
            User user, int page, int size, String type, String from, String to) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<WalletTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Base Security rule: Only wallet belonging to the requested user
            predicates.add(cb.equal(root.get("wallet").get("user").get("id"), user.getId()));

            // 2. Type Filter
            if (type != null && !type.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("transactionCategory"), type));
            }

            // 3. Date Filters
            if (from != null && !from.trim().isEmpty()) {
                LocalDateTime fromDate = LocalDate.parse(from, DateTimeFormatter.ISO_DATE).atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (to != null && !to.trim().isEmpty()) {
                LocalDateTime toDate = LocalDate.parse(to, DateTimeFormatter.ISO_DATE).atTime(23, 59, 59);
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<WalletTransaction> transactionPage = walletTransactionRepository.findAll(spec, pageRequest);

        List<TransactionHistoryResponse.TransactionSummary> summaries = transactionPage.getContent().stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());

        return TransactionHistoryResponse.builder()
                .success(true)
                .data(TransactionHistoryResponse.TransactionPageData.builder()
                        .transactions(summaries)
                        .totalPages(transactionPage.getTotalPages())
                        .totalElements(transactionPage.getTotalElements())
                        .currentPage(transactionPage.getNumber())
                        .build())
                .build();
    }

    public TransactionDetailResponse getTransactionDetail(Long id, User user) {
        WalletTransaction wt = walletTransactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (!wt.getWallet().getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You do not have permission to view this transaction");
        }

        TransactionDetailResponse.TransactionDetailData detail = TransactionDetailResponse.TransactionDetailData.builder()
                .id(wt.getId())
                .type(wt.getTransactionCategory())
                .amount(wt.getAmount())
                .status(wt.getStatus())
                .direction(wt.getDirection().name())
                .cardUid(wt.getCardUid())
                .createdAt(wt.getCreatedAt())
                .build();

        // Compute Sender/Receiver dynamically
        if (wt.getDirection() == TransactionType.DEBIT) {
            detail.setSenderName(user.getUsername());
            detail.setReceiverName(wt.getCounterpartyName());
        } else {
            detail.setSenderName(wt.getCounterpartyName());
            detail.setReceiverName(user.getUsername());
        }

        // Try to enrich with deepest reference info if it's an NFC payment
        if ("NFC_PAYMENT".equals(wt.getTransactionCategory()) && wt.getReference() != null && wt.getReference().startsWith("Payment: ")) {
            try {
                Long paymentId = Long.valueOf(wt.getReference().replace("Payment: ", "").trim());
                Payment parentPayment = paymentRepository.findById(paymentId).orElse(null);
                if (parentPayment != null) {
                    detail.setMerchantId(parentPayment.getMerchant().getId());
                }
            } catch (NumberFormatException ignored) {}
        }

        return TransactionDetailResponse.builder()
                .success(true)
                .data(detail)
                .build();
    }

    private TransactionHistoryResponse.TransactionSummary mapToSummary(WalletTransaction wt) {
        return TransactionHistoryResponse.TransactionSummary.builder()
                .id(wt.getId())
                .type(wt.getTransactionCategory())
                .amount(wt.getAmount())
                .status(wt.getStatus())
                .direction(wt.getDirection().name())
                .counterparty(wt.getCounterpartyName())
                .cardUid(wt.getCardUid())
                .createdAt(wt.getCreatedAt())
                .build();
    }
}
