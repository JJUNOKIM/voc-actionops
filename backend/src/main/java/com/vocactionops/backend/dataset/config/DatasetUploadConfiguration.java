package com.vocactionops.backend.dataset.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatasetUploadProperties.class)
public class DatasetUploadConfiguration {
}
