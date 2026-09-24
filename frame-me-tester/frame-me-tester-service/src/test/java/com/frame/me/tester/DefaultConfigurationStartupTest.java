package com.frame.me.tester;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class DefaultConfigurationStartupTest {

    @Test
    void defaultConfigurationStartsWithoutExternalInfrastructure() {
    }
}
