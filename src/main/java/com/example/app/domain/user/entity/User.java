package com.example.app.domain.user.entity;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
		name = "users",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_users_provider_provider_id",
				columnNames = {"provider", "provider_id"}
		)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false)
	private Provider provider;

	@Column(name = "provider_id", nullable = false, length = 100)
	private String providerId;

	@Column(nullable = false, length = 50)
	private String nickname;

	@Column(name = "profile_image_url", length = 500)
	private String profileImageUrl;

	@Column(name = "terms_agreed_at")
	private LocalDateTime termsAgreedAt;

	@Column(name = "marketing_email_agreed", nullable = false)
	@ColumnDefault("false")
	private boolean marketingEmailAgreed;

	@Column(name = "marketing_sns_agreed", nullable = false)
	@ColumnDefault("false")
	private boolean marketingSnsAgreed;

	@Builder
	private User(Provider provider, String providerId, String nickname, String profileImageUrl,
				  LocalDateTime termsAgreedAt, boolean marketingEmailAgreed, boolean marketingSnsAgreed) {
		this.provider = provider;
		this.providerId = providerId;
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
		this.termsAgreedAt = termsAgreedAt;
		this.marketingEmailAgreed = marketingEmailAgreed;
		this.marketingSnsAgreed = marketingSnsAgreed;
	}
}
