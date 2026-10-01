package com.github.guoyyit.accountlockoutjava.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LockoutProperties.class)
public class LockoutConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
