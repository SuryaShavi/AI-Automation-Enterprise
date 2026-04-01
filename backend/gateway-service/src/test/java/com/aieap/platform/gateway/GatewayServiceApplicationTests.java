package com.aieap.platform.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
    classes = GatewayServiceApplication.class,
    properties = {
        "security.jwt.secret=01234567890123456789012345678901",
        "spring.cloud.gateway.enabled=false",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
    }
)
class GatewayServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private RequestRateLimiterGatewayFilterFactory requestRateLimiterGatewayFilterFactory;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
