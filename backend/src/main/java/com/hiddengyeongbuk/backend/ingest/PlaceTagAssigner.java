package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * place_tag(자연/음식/전통/사찰/축제/휴식)를 카테고리·제목·설명 텍스트 키워드 규칙으로 채운다.
 * TourAPI의 신분류체계(lclsSystm) 코드는 실제 응답 필드를 별도 실측해야 해서(외부 API 연동
 * 설계서 6장 후속 과제) 이번 범위에서는 제외하고, 추가 API 호출이 필요 없는 키워드 매칭으로
 * MVP 수준의 태그를 부여한다.
 */
@Component
public class PlaceTagAssigner {

    private static final String TAG_NATURE = "자연";
    private static final String TAG_FOOD = "음식";
    private static final String TAG_TRADITION = "전통";
    private static final String TAG_TEMPLE = "사찰";
    private static final String TAG_FESTIVAL = "축제";
    private static final String TAG_REST = "휴식";

    private static final List<String> TEMPLE_KEYWORDS = List.of("사찰", "암자", "본찰", "대웅전");
    private static final List<String> TEMPLE_TITLE_SUFFIXES = List.of("사", "암");
    private static final List<String> TRADITION_KEYWORDS =
            List.of("한옥", "고택", "서원", "향교", "종택", "정자", "누각", "읍성", "유적", "고분", "전통마을");
    private static final List<String> NATURE_KEYWORDS =
            List.of("산", "계곡", "폭포", "호수", "저수지", "공원", "휴양림", "생태", "숲", "해변", "바다", "강");

    public Set<String> assign(PlaceCategory category, String titleKo, String overviewKo) {
        if (category == PlaceCategory.음식점) {
            return Set.of(TAG_FOOD);
        }
        if (category == PlaceCategory.숙박) {
            return Set.of(TAG_REST);
        }
        if (category == PlaceCategory.축제) {
            return Set.of(TAG_FESTIVAL);
        }
        return assignForAttraction(titleKo, overviewKo);
    }

    private Set<String> assignForAttraction(String titleKo, String overviewKo) {
        String title = titleKo == null ? "" : titleKo;
        String text = title + " " + (overviewKo == null ? "" : overviewKo);

        Set<String> tags = new LinkedHashSet<>();
        if (containsAny(text, TEMPLE_KEYWORDS) || endsWithAny(title, TEMPLE_TITLE_SUFFIXES)) {
            tags.add(TAG_TEMPLE);
        }
        if (containsAny(text, TRADITION_KEYWORDS)) {
            tags.add(TAG_TRADITION);
        }
        if (containsAny(text, NATURE_KEYWORDS)) {
            tags.add(TAG_NATURE);
        }

        if (tags.isEmpty()) {
            tags.add(TAG_NATURE);
        }
        return tags;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private static boolean endsWithAny(String title, List<String> suffixes) {
        return suffixes.stream().anyMatch(title::endsWith);
    }
}
