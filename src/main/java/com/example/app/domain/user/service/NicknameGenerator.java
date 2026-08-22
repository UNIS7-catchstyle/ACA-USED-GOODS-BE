package com.example.app.domain.user.service;

import com.example.app.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NicknameGenerator {

	private static final int MAX_RETRY = 5;
	private static final int DEFAULT_DIGIT_LENGTH = 4;
	private static final int FALLBACK_DIGIT_LENGTH = 6;

	private static final List<String> ADJECTIVES = List.of(
			"행복한", "즐거운", "신나는", "포근한", "상큼한",
			"반짝이는", "몽글몽글한", "씩씩한", "느긋한", "상냥한",
			"든든한", "폭신한", "활발한", "다정한", "엉뚱한",
			"용감한", "차분한", "명랑한", "새침한", "수줍은",
			"넉넉한", "산뜻한", "재빠른", "여유로운", "부지런한",
			"얌전한", "통통한", "싱그러운", "덕질하는", "꼼꼼한"
	);

	private static final List<String> NOUNS = List.of(
			"펭귄", "고양이", "강아지", "토끼", "다람쥐",
			"수달", "여우", "곰돌이", "판다", "고슴도치",
			"부엉이", "참새", "오리", "병아리", "하마",
			"코알라", "알파카", "라쿤", "두더지", "물개",
			"햄스터", "청설모", "앵무새", "사막여우", "거북이",
			"달팽이", "나비", "구름", "별빛", "초코칩"
	);

	private final UserRepository userRepository;
	private final SecureRandom random = new SecureRandom();

	public String generate() {
		for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
			String candidate = buildNickname(DEFAULT_DIGIT_LENGTH);
			if (!userRepository.existsByNickname(candidate)) {
				return candidate;
			}
		}

		String candidate;
		do {
			candidate = buildNickname(FALLBACK_DIGIT_LENGTH);
		} while (userRepository.existsByNickname(candidate));
		return candidate;
	}

	private String buildNickname(int digitLength) {
		String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
		String noun = NOUNS.get(random.nextInt(NOUNS.size()));
		int bound = (int) Math.pow(10, digitLength);
		String number = String.format("%0" + digitLength + "d", random.nextInt(bound));
		return adjective + " " + noun + number;
	}
}
