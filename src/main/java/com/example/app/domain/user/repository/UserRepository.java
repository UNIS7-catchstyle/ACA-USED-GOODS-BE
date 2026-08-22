package com.example.app.domain.user.repository;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByProviderAndProviderIdAndDeletedAtIsNull(Provider provider, String providerId);

	Optional<User> findByIdAndDeletedAtIsNull(Long id);

	boolean existsByNickname(String nickname);
}
