package com.example.app.domain.market.entity;

import com.example.app.domain.user.entity.User;
import com.example.app.global.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(
		name = "markets",
		uniqueConstraints = @UniqueConstraint(name = "uk_markets_user_id", columnNames = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Market extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false)
	private Category category;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(name = "item_categories", length = 200)
	private String itemCategories;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String description;

	@Column(name = "is_closed", nullable = false)
	@ColumnDefault("false")
	private boolean isClosed;

	@Column(name = "scrap_count", nullable = false)
	@ColumnDefault("0")
	private Integer scrapCount;

	@OneToMany(mappedBy = "market", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder ASC")
	private List<MarketImage> images = new ArrayList<>();

	@Builder
	private Market(User user, Category category, String title, String itemCategories, String description) {
		this.user = user;
		this.category = category;
		this.title = title;
		this.itemCategories = itemCategories;
		this.description = description;
		this.isClosed = false;
		this.scrapCount = 0;
	}

	public void increaseScrapCount() {
		this.scrapCount++;
	}

	public void decreaseScrapCount() {
		if (this.scrapCount > 0) {
			this.scrapCount--;
		}
	}
}
