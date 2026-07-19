package com.hiddengyeongbuk.backend.ingest;

/**
 * EngService2 목록의 영문 title에 괄호로 붙어 오는 국문명을 뽑는다. TourAPI 영문 제목은
 * "영문명 (국문명)" 형태로 국문명을 함께 담고 있어, 이걸 기존 국문 place와 매칭하는 핵심 키로 쓴다.
 *
 * 예:
 *  "Andong Dam (안동댐)" -> "안동댐"
 *  "Andong Gunja Village (Ocheon Historic Site) (안동 군자마을(오천유적지))" -> "안동 군자마을(오천유적지)"
 *  "A HILO Andong Branch[Tax Refund Shop](아일로 안동점)" -> "아일로 안동점"
 *
 * 규칙: 최상위(depth 1) 괄호 그룹 중 한글을 포함한 "가장 오른쪽" 그룹의 내용을 돌려준다.
 * 영문 부제가 앞쪽 괄호에 오고 국문명이 맨 끝 괄호에 오는 실제 패턴을 반영한다. 중첩 괄호는
 * 그룹 내용에 그대로 포함시킨다(국문명 자체가 괄호를 품는 경우가 있어서). 한글 괄호가 없으면 null.
 */
public final class KoreanNameExtractor {

    private KoreanNameExtractor() {}

    public static String extract(String engTitle) {
        if (engTitle == null) {
            return null;
        }
        int depth = 0;
        int groupStart = -1;
        String lastHangulGroup = null;
        for (int i = 0; i < engTitle.length(); i++) {
            char c = engTitle.charAt(i);
            if (c == '(') {
                if (depth == 0) {
                    groupStart = i + 1;
                }
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
                if (depth == 0 && groupStart >= 0) {
                    String content = engTitle.substring(groupStart, i);
                    if (containsHangul(content)) {
                        lastHangulGroup = content;
                    }
                    groupStart = -1;
                }
            }
        }
        if (lastHangulGroup == null) {
            return null;
        }
        String trimmed = lastHangulGroup.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 매칭 비교용 정규화: 모든 공백 제거(국문명과 titleKo의 띄어쓰기 차이를 흡수). */
    public static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String stripped = name.replaceAll("\\s+", "");
        return stripped.isEmpty() ? null : stripped;
    }

    private static boolean containsHangul(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // 한글 음절(가~힣) + 자모/호환 자모 영역.
            if ((c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x1100 && c <= 0x11FF) || (c >= 0x3130 && c <= 0x318F)) {
                return true;
            }
        }
        return false;
    }
}
