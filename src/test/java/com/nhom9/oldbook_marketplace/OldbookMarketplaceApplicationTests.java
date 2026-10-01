package com.nhom9.oldbook_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "catalog-test"})
class OldbookMarketplaceApplicationTests {

	@Test
	void contextLoads() {
	}
}
