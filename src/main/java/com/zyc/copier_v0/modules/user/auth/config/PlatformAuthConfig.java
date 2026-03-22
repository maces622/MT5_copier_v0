package com.zyc.copier_v0.modules.user.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PlatformAuthConfig {

    @Bean
    public PasswordEncoder platformPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
