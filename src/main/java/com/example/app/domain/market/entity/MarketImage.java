package com.example.app.domain.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "market_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "market_id", nullable = false)
	private Market market;

	@Column(name = "image_url", nullable = false, length = 500)
	private String imageUrl;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Builder
	private MarketImage(Market market, String imageUrl, Integer sortOrder) {
		this.market = market;
		this.imageUrl = imageUrl;
		this.sortOrder = sortOrder;
	}
}
