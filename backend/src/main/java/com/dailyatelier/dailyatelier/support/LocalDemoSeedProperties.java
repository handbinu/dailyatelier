package com.dailyatelier.dailyatelier.support;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "dailyatelier.local-demo")
public class LocalDemoSeedProperties {
    private boolean enabled;
}
