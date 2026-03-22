package com.zyc.copier_v0.modules.user.auth.config;

import java.time.Duration;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "copier.auth.session")
@Getter
@Setter
public class PlatformAuthProperties {

    @NotBlank
    private String cookieName = "COPIER_SESSION";

    @NotNull
    private Duration ttl = Duration.ofDays(7);

    private boolean secure = false;
}
