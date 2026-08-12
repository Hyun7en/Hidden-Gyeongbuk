package com.hiddengyeongbuk.backend.support;


import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 슬라이스 테스트({@code @WebMvcTest})는 {@code @ConfigurationProperties} 빈을 자동으로
 * 올리지 않는다. lang 해석은 컨트롤러 동작의 일부라 목으로 대체하면 검증 의미가 없어서,
 * 실제 레지스트리를 ko/en으로 채워 넣는다.
 */
@TestConfiguration
public class LanguageRegistryTestConfig {

    @Bean
    public LanguageRegistry languageRegistry() {
        LanguageRegistry registry = new LanguageRegistry();
        registry.setEntries(List.of(entry("ko", "한국어", "KorService2"), entry("en", "English", "EngService2")));
        registry.index();
        return registry;
    }

    private static LanguageRegistry.Entry entry(String code, String displayName, String serviceName) {
        LanguageRegistry.Entry entry = new LanguageRegistry.Entry();
        entry.setCode(code);
        entry.setDisplayName(displayName);
        entry.setServiceName(serviceName);
        entry.setActive(true);
        return entry;
    }
}
