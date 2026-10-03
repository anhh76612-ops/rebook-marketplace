package com.oldbook;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.config.import=")
@ActiveProfiles({"test", "catalog-test"})
class OldbookMarketplaceApplicationTests {

	@Test
	void contextLoads() {
	}
}
