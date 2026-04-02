package com.umesh.unipay_1.repository;

import com.umesh.unipay_1.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByCardUid(String cardUid);

    boolean existsByCardUid(String cardUid);

    List<Card> findAllByUserId(Long userId);

    List<Card> findAllByIsActiveTrue();
}
