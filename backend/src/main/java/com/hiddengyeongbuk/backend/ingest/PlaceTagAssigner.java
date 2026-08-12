package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.domain.place.PlaceTagType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * place_tag({@link PlaceTagType} 어휘)를 타입·제목·설명 키워드 규칙으로 채운다.
 *
 * <p><b>저장 값은 enum 코드다</b>(NATURE 등). 구 구현은 한글 라벨("자연")을 DB에 넣었는데,
 * 다국어 응답에 특정 언어 문자열이 섞이는 문제가 있어 ERD §3.8이 이를 금지했다. 표시 라벨은
 * 프론트 i18n 책임이다.
 *
 * <p>판정은 국문 텍스트 기준이다 — 키워드 규칙이 한국어라, Merge 단계에서 ko 번역의
 * 제목·설명을 넘겨받아 부여한다. 국문이 없는 장소(다른 언어에만 존재)는 태그가 비게 되며,
 * 이는 lclsSystm 기반 개편으로 해소할 후속 과제다(ERD §9).
 *
 * <p>무료 로컬 연산이므로 수집 때마다 다시 계산한다 — 규칙을 바꾸면 다음 수집에 자동 반영된다.
 */
@Component
public class PlaceTagAssigner {

    private static final String TAG_NATURE = PlaceTagType.NATURE.name();
    private static final String TAG_FOOD = PlaceTagType.FOOD.name();
    private static final String TAG_TRADITION = PlaceTagType.TRADITION.name();
    private static final String TAG_TEMPLE = PlaceTagType.TEMPLE.name();
    private static final String TAG_FESTIVAL = PlaceTagType.FESTIVAL.name();
    private static final String TAG_REST = PlaceTagType.REST.name();

    private static final List<String> TEMPLE_KEYWORDS = List.of("사찰", "암자", "본찰", "대웅전");
    private static final List<String> TEMPLE_TITLE_SUFFIXES = List.of("사", "암");
    private static final List<String> TRADITION_KEYWORDS =
            List.of("한옥", "고택", "서원", "향교", "종택", "정자", "누각", "읍성", "유적", "고분", "전통마을");
    private static final List<String> NATURE_KEYWORDS =
            List.of("산", "계곡", "폭포", "호수", "저수지", "공원", "휴양림", "생태", "숲", "해변", "바다", "강");
    private static final List<String> MARKET_KEYWORDS = List.of("시장", "오일장");
    // TourAPI는 동명이인 장소를 "개목사(안동)"처럼 지역명 괄호로 구분한다 — 접미사 매칭 전에 제거한다.
    private static final Pattern TRAILING_PAREN = Pattern.compile("\\s*\\([^)]*\\)$");

    public Set<String> assign(PlaceType placeType, String titleKo, String overviewKo) {
        return switch (placeType) {
            case RESTAURANT -> Set.of(TAG_FOOD);
            case STAY -> Set.of(TAG_REST);
            case FESTIVAL -> Set.of(TAG_FESTIVAL);
            case SHOPPING -> assignForShopping(titleKo, overviewKo);
            case ATTRACTION, CULTURAL, LEISURE -> assignForAttraction(titleKo, overviewKo);
        };
    }

    private Set<String> assignForShopping(String titleKo, String overviewKo) {
        String text = (titleKo == null ? "" : titleKo) + " " + (overviewKo == null ? "" : overviewKo);
        // 전통시장류만 "전통" 태그를 부여한다. 나머지 쇼핑(상점가 등)은 기존 6개 태그
        // 분류 어디에도 맞지 않아 태그 없이 카테고리/지역으로만 조회되게 둔다.
        if (containsAny(text, MARKET_KEYWORDS)) {
            return Set.of(TAG_TRADITION);
        }
        return Set.of();
    }

    private Set<String> assignForAttraction(String titleKo, String overviewKo) {
        String title = titleKo == null ? "" : titleKo;
        String normalizedTitle = TRAILING_PAREN.matcher(title).replaceAll("");
        String text = title + " " + (overviewKo == null ? "" : overviewKo);

        Set<String> tags = new LinkedHashSet<>();
        if (containsAny(text, TEMPLE_KEYWORDS) || endsWithAny(normalizedTitle, TEMPLE_TITLE_SUFFIXES)) {
            tags.add(TAG_TEMPLE);
        }
        if (containsAny(text, TRADITION_KEYWORDS)) {
            tags.add(TAG_TRADITION);
        }
        if (containsAny(text, NATURE_KEYWORDS)) {
            tags.add(TAG_NATURE);
        }

        // 아무 키워드에도 안 걸리면 무태그로 둔다(쇼핑과 동일). 예전엔 "자연"을 기본값으로
        // 부여했는데, 박물관·전시관까지 자연 코스에 섞여 들어가 관심사 매칭을 오염시켰다.
        // 무태그 장소는 관심사 필터에는 안 걸리지만 카테고리·지역 조회와 코스 후보에는 그대로 포함된다.
        return tags;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private static boolean endsWithAny(String title, List<String> suffixes) {
        return suffixes.stream().anyMatch(title::endsWith);
    }
}
