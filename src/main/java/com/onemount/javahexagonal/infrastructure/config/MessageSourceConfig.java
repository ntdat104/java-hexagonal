package com.onemount.javahexagonal.infrastructure.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

@Configuration
public class MessageSourceConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(
                "classpath:errors",     // ✅ loads errors.properties
                "classpath:messages"    // ✅ optional if you also use messages.properties
        );
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }
}
