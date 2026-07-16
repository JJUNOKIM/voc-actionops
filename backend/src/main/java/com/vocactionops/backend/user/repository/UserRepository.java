package com.vocactionops.backend.user.repository;

import com.vocactionops.backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmailIgnoreCase(String email);

	List<User> findAllByOrganizationId(Long organizationId);

	boolean existsByEmailIgnoreCase(String email);
}
