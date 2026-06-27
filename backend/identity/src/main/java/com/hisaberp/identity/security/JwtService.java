package com.hisaberp.identity.security;

import com.hisaberp.shared.security.CurrentUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties props;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(CurrentUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.accessTokenTtl());
        return Jwts.builder()
                .issuer(props.issuer())
                .audience().add(props.audience()).and()
                .subject(user.userId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("tid", user.tenantId() == null ? null : user.tenantId().toString())
                .claim("email", user.email())
                .claim("lang", user.preferredLanguage())
                .claim("roles", user.roles())
                .claim("perms", user.permissions())
                .signWith(key())
                .compact();
    }

    public CurrentUser parseAndValidate(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key())
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token);
        Claims c = jws.getPayload();

        if (c.getExpiration() != null && c.getExpiration().toInstant().isBefore(Instant.now())) {
            throw new JwtException("Token expired");
        }

        UUID userId = UUID.fromString(c.getSubject());
        String tidStr = c.get("tid", String.class);
        UUID tenantId = tidStr == null ? null : UUID.fromString(tidStr);

        @SuppressWarnings("unchecked")
        List<String> roles = c.get("roles", List.class);
        @SuppressWarnings("unchecked")
        List<String> perms = c.get("perms", List.class);

        return new CurrentUser(
                userId,
                tenantId,
                c.get("email", String.class),
                c.get("lang", String.class),
                roles == null ? Set.of() : Set.copyOf(roles),
                perms == null ? Set.of() : Set.copyOf(perms));
    }
}
