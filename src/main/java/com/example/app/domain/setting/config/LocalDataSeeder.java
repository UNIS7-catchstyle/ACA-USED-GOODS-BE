package com.example.app.domain.setting.config;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.domain.comment.entity.Comment;
import com.example.app.domain.comment.repository.CommentRepository;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import com.example.app.domain.market.entity.MarketImage;
import com.example.app.domain.market.repository.MarketImageRepository;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.scrap.entity.Scrap;
import com.example.app.domain.scrap.repository.ScrapRepository;
import com.example.app.domain.user.entity.User;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.domain.user.service.NicknameGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Frontend fixture data for local development only — never runs outside the
// "local" profile, and skips entirely once any user exists so restarts don't
// keep piling up duplicate rows.
@Slf4j
@Profile("local")
@Component
@RequiredArgsConstructor
public class LocalDataSeeder implements ApplicationRunner {

	// 10 market-owning users (indexes 0-9) + 3 non-owning users who agreed to
	// terms (10-12) + 1 non-owning user who has NOT agreed to terms (13, for
	// exercising the terms-agreement flow) = 14 total.
	private static final int MARKET_OWNER_COUNT = 10;
	private static final int TOTAL_USER_COUNT = 14;

	// KPOP x4, TWO_D x3, MUSICAL x2, ETC x1 = 10 markets, one per owner above.
	private static final Category[] MARKET_CATEGORIES = {
			Category.KPOP, Category.KPOP, Category.KPOP, Category.KPOP,
			Category.TWO_D, Category.TWO_D, Category.TWO_D,
			Category.MUSICAL, Category.MUSICAL,
			Category.ETC
	};
	private static final List<Integer> CLOSED_MARKET_INDEXES = List.of(2, 5, 8);
	private static final int[] IMAGE_COUNTS_PER_MARKET = {2, 0, 1, 3, 0, 2, 1, 3, 0, 2};
	private static final int[] SCRAP_COUNTS_PER_MARKET = {3, 0, 5, 1, 2, 4, 0, 3, 1, 2};

	private final UserRepository userRepository;
	private final MarketRepository marketRepository;
	private final MarketImageRepository marketImageRepository;
	private final CommentRepository commentRepository;
	private final ScrapRepository scrapRepository;
	private final NicknameGenerator nicknameGenerator;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.count() > 0) {
			log.info("LocalDataSeeder: users already exist, skip seeding");
			return;
		}

		List<User> users = seedUsers();
		List<Market> markets = seedMarkets(users);
		List<MarketImage> images = seedImages(markets);
		List<Comment> comments = seedComments(users, markets);
		List<Scrap> scraps = seedScraps(users, markets);

		log.info("LocalDataSeeder: seeded users={}, markets={}, images={}, comments={}, scraps={}",
				users.size(), markets.size(), images.size(), comments.size(), scraps.size());
	}

	private List<User> seedUsers() {
		List<User> users = new ArrayList<>();
		for (int i = 0; i < TOTAL_USER_COUNT; i++) {
			boolean agreedToTerms = i != TOTAL_USER_COUNT - 1;
			User user = User.builder()
					.provider(Provider.KAKAO)
					// "seed_" prefix keeps these from ever colliding with a real OAuth
					// provider_id, since FakeOAuthClient (test only) and real Kakao/Google
					// ids never take this shape.
					.providerId("seed_" + (i + 1))
					.nickname(nicknameGenerator.generate())
					.termsAgreedAt(agreedToTerms ? LocalDateTime.now() : null)
					.marketingEmailAgreed(false)
					.marketingSnsAgreed(false)
					.build();
			users.add(userRepository.saveAndFlush(user));
		}
		return users;
	}

	private List<Market> seedMarkets(List<User> users) {
		List<Market> markets = new ArrayList<>();
		for (int i = 0; i < MARKET_OWNER_COUNT; i++) {
			Category category = MARKET_CATEGORIES[i];
			Market market = Market.builder()
					.user(users.get(i))
					.category(category)
					.title(category.name() + " 굿즈 마켓 " + (i + 1))
					.itemCategories("포토카드,앨범,굿즈")
					.description(category.name() + " 카테고리 시드 마켓입니다. 로컬 개발용으로 생성된 데이터입니다.")
					.build();
			if (CLOSED_MARKET_INDEXES.contains(i)) {
				market.update(category, market.getTitle(), market.getItemCategories(), market.getDescription(), true);
			}
			markets.add(marketRepository.saveAndFlush(market));
		}
		return markets;
	}

	private List<MarketImage> seedImages(List<Market> markets) {
		List<MarketImage> images = new ArrayList<>();
		int counter = 1;
		for (int i = 0; i < markets.size(); i++) {
			Market market = markets.get(i);
			for (int sortOrder = 0; sortOrder < IMAGE_COUNTS_PER_MARKET[i]; sortOrder++) {
				MarketImage image = MarketImage.builder()
						.market(market)
						.imageUrl("http://localhost:8080/uploads/seed/" + counter + ".png")
						.sortOrder(sortOrder)
						.build();
				images.add(marketImageRepository.save(image));
				counter++;
			}
		}
		marketImageRepository.flush();
		return images;
	}

	// Builds a 3-market comment section: market 0 gets a root -> reply -> grandchild
	// thread (the required 3-level case) plus a second root/reply pair; markets 1
	// and 2 get flatter root/reply mixes.
	private List<Comment> seedComments(List<User> users, List<Market> markets) {
		List<Comment> comments = new ArrayList<>();

		Market market0 = markets.get(0);
		User owner0 = users.get(0);
		Comment root1 = saveComment(comments, market0, users.get(3), null, "인기 많네요 소통해요!");
		Comment reply1 = saveComment(comments, market0, owner0, root1, "네 감사합니다 :)");
		saveComment(comments, market0, users.get(3), reply1, "빠른 답변 감사해요");
		Comment root2 = saveComment(comments, market0, users.get(4), null, "가격 네고 가능한가요?");
		saveComment(comments, market0, owner0, root2, "죄송하지만 네고는 어렵습니다");

		Market market1 = markets.get(1);
		User owner1 = users.get(1);
		Comment m1Root1 = saveComment(comments, market1, users.get(5), null, "오늘 발송해주시나요?");
		saveComment(comments, market1, owner1, m1Root1, "네 오늘 바로 발송했습니다");
		saveComment(comments, market1, users.get(6), null, "실물 상태 어떤가요?");
		Comment m1Root3 = saveComment(comments, market1, users.get(7), null, "직거래 가능할까요?");
		saveComment(comments, market1, owner1, m1Root3, "네 가능합니다");

		Market market2 = markets.get(2);
		User owner2 = users.get(2);
		Comment m2Root1 = saveComment(comments, market2, users.get(8), null, "마감되었나요?");
		saveComment(comments, market2, owner2, m2Root1, "네 마감되었습니다");
		saveComment(comments, market2, users.get(9), null, "재입고 예정 있으신가요?");

		return comments;
	}

	private Comment saveComment(List<Comment> accumulator, Market market, User author, Comment parent, String content) {
		Comment comment = Comment.builder()
				.market(market)
				.user(author)
				.parent(parent)
				.content(content)
				.build();
		Comment saved = commentRepository.saveAndFlush(comment);
		accumulator.add(saved);
		return saved;
	}

	// Runs last: incrementScrapCount() flushes and clears the persistence context on
	// every call, so any entity held from earlier steps would become detached — fine
	// here since nothing after this needs to touch users/markets again.
	private List<Scrap> seedScraps(List<User> users, List<Market> markets) {
		List<Scrap> scraps = new ArrayList<>();
		for (int i = 0; i < markets.size(); i++) {
			Market market = markets.get(i);
			User owner = users.get(i);
			for (User scraper : pickScrapers(users, owner, SCRAP_COUNTS_PER_MARKET[i], i)) {
				Scrap scrap = Scrap.builder().user(scraper).market(market).build();
				scraps.add(scrapRepository.save(scrap));
				marketRepository.incrementScrapCount(market.getId());
			}
		}
		return scraps;
	}

	// Deterministic, owner-excluded, distinct-per-market selection — the pool (13
	// non-owner users) always comfortably covers the largest count used (5).
	private List<User> pickScrapers(List<User> users, User owner, int count, int offset) {
		List<User> pool = new ArrayList<>();
		for (User user : users) {
			if (!user.getId().equals(owner.getId())) {
				pool.add(user);
			}
		}
		List<User> selected = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			selected.add(pool.get((offset + i) % pool.size()));
		}
		return selected;
	}
}
