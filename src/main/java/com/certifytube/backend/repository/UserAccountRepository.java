package com.certifytube.backend.repository;

import com.certifytube.backend.model.Role;
import com.certifytube.backend.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);
    List<UserAccount> findByRoleOrderByCreatedAtUtcDesc(Role role);
    long countByRole(Role role);
}
