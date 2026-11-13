package com.petcommerce.user.repository;

import com.petcommerce.user.entity.User;
import com.petcommerce.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmailIgnoreCase(String email);

    long countByRoleAndActiveTrue(Role role);
}
