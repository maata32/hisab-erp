package com.hisaberp.identity.security;

import com.hisaberp.shared.audit.RequestContext;
import com.hisaberp.shared.security.CurrentUser;
import com.hisaberp.shared.security.CurrentUserHolder;
import com.hisaberp.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            String header = req.getHeader("Authorization");
            String traceId = req.getHeader("X-Trace-Id");
            RequestContext.set(clientIp(req), req.getHeader("User-Agent"), traceId);

            if (header != null && header.startsWith(BEARER)) {
                String token = header.substring(BEARER.length());
                try {
                    CurrentUser user = jwtService.parseAndValidate(token);
                    CurrentUserHolder.set(user);
                    if (user.tenantId() != null) {
                        TenantContext.set(user.tenantId());
                    }
                    if (user.preferredLanguage() != null && !user.preferredLanguage().isBlank()) {
                        LocaleContextHolder.setLocale(Locale.forLanguageTag(user.preferredLanguage()));
                    }
                    Set<SimpleGrantedAuthority> authorities = Stream.concat(
                                    user.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)),
                                    user.permissions().stream().map(SimpleGrantedAuthority::new))
                            .collect(Collectors.toSet());
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception e) {
                    log.debug("Rejected JWT: {}", e.getMessage());
                }
            }
            chain.doFilter(req, res);
        } finally {
            CurrentUserHolder.clear();
            TenantContext.clear();
            RequestContext.clear();
            SecurityContextHolder.clearContext();
            LocaleContextHolder.resetLocaleContext();
        }
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
