package com.hisaberp.shared.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Configuration
public class MessagesConfig {

    public static final Locale FRENCH = Locale.forLanguageTag("fr");
    public static final Locale ARABIC = Locale.forLanguageTag("ar");
    public static final Locale ENGLISH = Locale.ENGLISH;

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasenames(
                "classpath:i18n/messages",
                "classpath:i18n/errors",
                "classpath:i18n/notifications");
        ms.setDefaultEncoding(StandardCharsets.UTF_8.name());
        ms.setUseCodeAsDefaultMessage(true);
        ms.setFallbackToSystemLocale(false);
        ms.setCacheSeconds(60);
        return ms;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(FRENCH, ARABIC, ENGLISH));
        resolver.setDefaultLocale(FRENCH);
        return resolver;
    }
}
