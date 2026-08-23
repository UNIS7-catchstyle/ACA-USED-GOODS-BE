package com.example.app.domain.market.service;

import com.example.app.domain.comment.repository.CommentRepository;
import com.example.app.domain.image.storage.ImageStorage;
import com.example.app.domain.market.dto.MarketDetail;
import com.example.app.domain.market.dto.MarketIdResponse;
import com.example.app.domain.market.dto.MarketRequest;
import com.example.app.domain.market.dto.MarketSummary;
import com.example.app.domain.market.dto.RegistrationStatusResponse;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import com.example.app.domain.market.entity.MarketImage;
import com.example.app.domain.market.repository.MarketImageRepository;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.scrap.repository.ScrapRepository;
import com.example.app.domain.setting.service.AppSettingService;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import com.example.app.global.paging.Cursor;
import com.example.app.global.paging.CursorPageResponse;
import com.example.app.global.transaction.AfterCommitRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

	private static final int THUMBNAIL_LIMIT = 3;

	private final MarketRepository marketRepository;
	private final MarketImageRepository marketImageRepository;
	private final ScrapRepository scrapRepository;
	private final CommentRepository commentRepository;
	private final UserRepository userRepository;
	private final AppSettingService appSettingService;
	private final ImageStorage imageStorage;

	@Transactional(readOnly = true)
	public CursorPageResponse<MarketSummary> getMarkets(Long userId, Category category, boolean excludeClosed, String cursorParam, int size) {
		LocalDateTime cursorCreatedAt = null;
		Long cursorId = null;
		if (StringUtils.hasText(cursorParam)) {
			try {
				Cursor cursor = Cursor.decode(cursorParam);
				cursorCreatedAt = cursor.createdAt();
				cursorId = cursor.id();
			} catch (IllegalArgumentException e) {
				throw new BusinessException(ErrorCode.INVALID_CURSOR);
			}
		}

		long totalCount = marketRepository.countByFilter(category, excludeClosed);
		List<Market> fetched = marketRepository.findPage(category, excludeClosed, cursorCreatedAt, cursorId, PageRequest.of(0, size + 1));

		boolean hasNext = fetched.size() > size;
		List<Market> pageMarkets = hasNext ? fetched.subList(0, size) : fetched;
		List<Long> marketIds = pageMarkets.stream().map(Market::getId).toList();

		Set<Long> scrappedMarketIds = (userId != null && !marketIds.isEmpty())
				? new HashSet<>(scrapRepository.findMarketIdsByUserIdAndMarketIdIn(userId, marketIds))
				: Set.of();
		Map<Long, List<String>> thumbnailsByMarketId = marketIds.isEmpty() ? Map.of() : groupThumbnails(marketIds);

		List<MarketSummary> items = pageMarkets.stream()
				.map(market -> MarketSummary.from(
						market,
						scrappedMarketIds.contains(market.getId()),
						thumbnailsByMarketId.getOrDefault(market.getId(), List.of())))
				.toList();

		String nextCursor = hasNext ? cursorOf(pageMarkets.get(pageMarkets.size() - 1)).encode() : null;
		return new CursorPageResponse<>(totalCount, items, nextCursor, hasNext);
	}

	@Transactional(readOnly = true)
	public RegistrationStatusResponse getRegistrationStatus() {
		return new RegistrationStatusResponse(appSettingService.isMarketRegistrationOpen());
	}

	@Transactional(readOnly = true)
	public MarketDetail getDetail(Long userId, Long marketId) {
		Market market = marketRepository.findByIdWithUser(marketId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
		boolean isScrapped = userId != null && scrapRepository.existsByUserIdAndMarketId(userId, marketId);
		boolean isOwner = userId != null && market.getUser().getId().equals(userId);
		List<String> images = imageUrlsOf(marketId);
		return MarketDetail.from(market, isScrapped, isOwner, images);
	}

	@Transactional
	public MarketIdResponse register(Long userId, MarketRequest request) {
		if (!appSettingService.isMarketRegistrationOpen()) {
			throw new BusinessException(ErrorCode.MARKET_REGISTRATION_CLOSED);
		}
		if (marketRepository.existsByUserId(userId)) {
			throw new BusinessException(ErrorCode.MARKET_ALREADY_EXISTS);
		}
		List<String> imageUrls = validateImageUrls(request.imageUrlsOrEmpty());

		Market market = Market.builder()
				.user(userRepository.getReferenceById(userId))
				.category(request.category())
				.title(request.title())
				.itemCategories(request.itemCategories())
				.description(request.description())
				.build();

		try {
			// IDENTITY generation flushes this INSERT immediately, so the unique
			// constraint violation (concurrent double-registration) surfaces right here.
			marketRepository.save(market);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.MARKET_ALREADY_EXISTS);
		}

		saveImages(market, imageUrls);
		return new MarketIdResponse(market.getId());
	}

	@Transactional
	public MarketDetail update(Long userId, Long marketId, MarketRequest request) {
		Market market = marketRepository.findByIdWithUser(marketId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
		if (!market.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		List<String> newImageUrls = validateImageUrls(request.imageUrlsOrEmpty());
		List<String> oldImageUrls = imageUrlsOf(marketId);

		market.update(request.category(), request.title(), request.itemCategories(), request.description(), request.isClosedOrFalse());

		// Explicit delete-then-reinsert rather than Market.images.clear()/addAll():
		// orphanRemoval on a collection that was never initialized in this session
		// has previously misbehaved (see UserService's withdraw() history).
		marketImageRepository.deleteByMarketId(marketId);
		marketImageRepository.flush();
		saveImages(market, newImageUrls);

		List<String> removedUrls = oldImageUrls.stream().filter(url -> !newImageUrls.contains(url)).toList();
		deleteImagesAfterCommit(removedUrls);

		boolean isScrapped = scrapRepository.existsByUserIdAndMarketId(userId, marketId);
		return MarketDetail.from(market, isScrapped, true, newImageUrls);
	}

	@Transactional
	public void deleteByOwner(Long userId) {
		marketRepository.findByUserId(userId).ifPresent(market -> {
			Long marketId = market.getId();
			List<String> imageUrls = imageUrlsOf(marketId);

			marketImageRepository.deleteByMarketId(marketId);
			commentRepository.deleteByMarketId(marketId);
			scrapRepository.deleteByMarketId(marketId);
			marketRepository.delete(market);

			deleteImagesAfterCommit(imageUrls);
		});
	}

	private List<String> validateImageUrls(List<String> imageUrls) {
		if (new HashSet<>(imageUrls).size() != imageUrls.size()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "중복된 이미지 URL이 있습니다.");
		}
		for (String url : imageUrls) {
			if (!imageStorage.isOwnedUrl(url)) {
				throw new BusinessException(ErrorCode.INVALID_IMAGE_URL);
			}
		}
		return imageUrls;
	}

	private void saveImages(Market market, List<String> imageUrls) {
		for (int i = 0; i < imageUrls.size(); i++) {
			marketImageRepository.save(MarketImage.builder()
					.market(market)
					.imageUrl(imageUrls.get(i))
					.sortOrder(i)
					.build());
		}
	}

	private List<String> imageUrlsOf(Long marketId) {
		return marketImageRepository.findByMarketIdInOrderBySortOrderAsc(List.of(marketId)).stream()
				.map(MarketImage::getImageUrl)
				.toList();
	}

	private Map<Long, List<String>> groupThumbnails(List<Long> marketIds) {
		Map<Long, List<String>> grouped = marketImageRepository.findByMarketIdInOrderBySortOrderAsc(marketIds).stream()
				.collect(Collectors.groupingBy(
						image -> image.getMarket().getId(),
						LinkedHashMap::new,
						Collectors.mapping(MarketImage::getImageUrl, Collectors.toList())));
		grouped.replaceAll((id, urls) -> urls.size() <= THUMBNAIL_LIMIT ? urls : urls.subList(0, THUMBNAIL_LIMIT));
		return grouped;
	}

	private Cursor cursorOf(Market market) {
		return new Cursor(market.getCreatedAt(), market.getId());
	}

	private void deleteImagesAfterCommit(List<String> imageUrls) {
		if (imageUrls.isEmpty()) {
			return;
		}
		AfterCommitRunner.run(() -> imageUrls.forEach(url -> {
			try {
				imageStorage.delete(url);
			} catch (Exception e) {
				log.warn("Failed to delete market image after commit: url={}", url, e);
			}
		}));
	}
}
