package com.minierp.shared.persistence;

import com.minierp.shared.security.CurrentUserHolder;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
class JpaAuditingConfig {

    @Component("auditorAware")
    static class CurrentUserAuditorAware implements AuditorAware<UUID> {
        @Override
        public Optional<UUID> getCurrentAuditor() {
            return CurrentUserHolder.tryGet().map(u -> u.userId());
        }
    }
}
