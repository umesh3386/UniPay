package com.umesh.unipay_1.repository;

import com.umesh.unipay_1.entity.User;
import com.umesh.unipay_1.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByFirebaseUid(String firebaseUid);
    Optional<User> findByRefreshToken(String refreshToken);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByStudentId(String studentId);
    List<User> findAllByRole(Role role);
    Page<User> findAllByRole(Role role, Pageable pageable);
}
