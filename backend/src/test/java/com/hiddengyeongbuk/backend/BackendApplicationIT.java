package com.hiddengyeongbuk.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 전체 스프링 컨텍스트 로딩 확인. Flyway가 기동 시 실제 MySQL에 접속하므로
 * 실제 DB(docker compose up -d mysql)가 필요한 통합 테스트다.
 *
 * 실행 방법:
 *   set -a; source ../.env; set +a
 *   ./gradlew integrationTest --tests "*BackendApplicationIT"
 */
@SpringBootTest
class BackendApplicationIT {

	@Test
	void contextLoads() {
	}

}
