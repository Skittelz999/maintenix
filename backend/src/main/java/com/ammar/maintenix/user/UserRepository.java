package com.ammar.maintenix.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllByOrderByEmailAsc();

    List<User> findAllByRoleOrderByEmailAsc(UserRole role);

    List<User> findAllByActiveOrderByEmailAsc(boolean active);

    List<User> findAllByRoleAndActiveOrderByEmailAsc(
            UserRole role,
            boolean active
    );
}
