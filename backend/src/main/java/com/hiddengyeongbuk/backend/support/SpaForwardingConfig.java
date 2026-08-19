package com.hiddengyeongbuk.backend.support;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * React 정적 번들 서빙 + SPA 폴백(아키텍처 §8, ADR-002 — "정적 번들은 백엔드에서 서빙한다").
 *
 * <p><b>왜 폴백이 필요한가.</b> 번들만 올려 두면 {@code /}는 뜨지만 {@code /regions/1} ·
 * {@code /places/5} · 추천 결과 공유 URL이 전부 404가 된다. 그 경로들은 서버에 파일로 존재하지
 * 않고 브라우저가 받은 뒤 React Router가 해석하는 주소이기 때문이다. 서버는 index.html을 돌려주고
 * 경로 해석은 클라이언트에 맡겨야 한다.
 *
 * <p>이게 특히 중요한 것은 <b>공유 URL이 무상태 재계산 방식</b>(FR-C-06, ADR-003/009)이라 링크를
 * 받은 사람이 그 주소로 <b>직접 진입</b>하기 때문이다. SPA 폴백이 없으면 공유 기능 자체가 성립하지
 * 않는다. 자체 뒤로가기를 없앤 것(ADR-022)도 같은 전제 위에 있다 — 모든 이동이 실제 URL이다.
 *
 * <p><b>API 경로는 폴백하지 않는다.</b> {@code /api/bogus}에 index.html을 200으로 돌려주면
 * 클라이언트가 JSON을 기대한 자리에서 HTML을 받아 파싱 에러가 나고, 원인이 "그런 API가 없다"는
 * 사실에서 멀어진다. 없는 API는 404여야 한다. {@code /internal}도 같은 이유로 제외한다 —
 * 운영 프로파일에서 그 빈이 아예 없는 것이 설계인데(@Profile("local")), 폴백이 200을 돌려주면
 * "엔드포인트가 뜨지 않는다"는 확인이 불가능해진다.
 *
 * <p>확장자가 있는 경로도 폴백하지 않는다 — 없는 {@code .js} · {@code .css} · {@code .png}에
 * HTML을 돌려주면 브라우저 콘솔에 엉뚱한 MIME 에러가 뜨고 진짜 원인(빌드 산출물 누락)이 가려진다.
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

    /** 폴백에서 제외할 경로. 여기 걸리면 정적 리소스로도, index.html로도 해석하지 않는다. */
    private static final String[] SERVER_ROUTES = {"api/", "internal/"};

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (isServerRoute(resourcePath) || hasExtension(resourcePath)) {
                            return null;
                        }
                        // 클라이언트 라우트다 — 셸을 돌려주고 해석은 React Router에 맡긴다.
                        Resource shell = new org.springframework.core.io.ClassPathResource("static/index.html");
                        return shell.exists() ? shell : null;
                    }
                });
    }

    private static boolean isServerRoute(String resourcePath) {
        for (String prefix : SERVER_ROUTES) {
            if (resourcePath.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 마지막 경로 조각에 점이 있으면 파일 요청으로 본다. {@code /regions/1}은 해당 없음. */
    private static boolean hasExtension(String resourcePath) {
        int lastSlash = resourcePath.lastIndexOf('/');
        return resourcePath.indexOf('.', lastSlash + 1) >= 0;
    }
}
