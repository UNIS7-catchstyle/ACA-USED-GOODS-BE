package com.example.app.domain.scrap.entity;

import com.example.app.domain.market.entity.Market;
import com.example.app.domain.user.entity.User;
import com.example.app.global.common.BaseCreatedTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
		name = "scraps",
		uniqueConstraints = @UniqueConstraint(name = "uk_scraps_user_id_market_id", columnNames = {"user_id", "market_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scrap extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "market_id", nullable = false)
	private Market market;

	@Builder
	private Scrap(User user, Market market) {
		this.user = user;
		this.market = market;
	}
}
