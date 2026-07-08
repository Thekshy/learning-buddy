package com.learningbuddy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用自定义配置类
 */
@Configuration
@EnableConfigurationProperties(PropertiesConfig.class)
public class AppConfig {
}
