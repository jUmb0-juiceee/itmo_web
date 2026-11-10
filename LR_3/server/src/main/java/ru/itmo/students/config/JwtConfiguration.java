package ru.itmo.students.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {
    @Bean public Clock authClock() { return Clock.systemUTC(); }
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public SecretKey jwtKey(JwtProperties properties) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(properties.secret()); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("JWT_SECRET must be Base64", exception); }
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET must encode at least 32 random bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean("accessJwtDecoder")
    public JwtDecoder accessJwtDecoder(SecretKey key, JwtProperties properties, Clock clock) {
        return decoder(key, properties.issuer(), properties.audience(), "access", clock);
    }

    @Bean("refreshJwtDecoder")
    public JwtDecoder refreshJwtDecoder(SecretKey key, JwtProperties properties, Clock clock) {
        return decoder(key, properties.issuer(), properties.refreshAudience(), "refresh", clock);
    }

    private JwtDecoder decoder(SecretKey key, String issuer, String audience, String type, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> purpose = token -> {
            boolean valid = type.equals(token.getClaimAsString("token_use")) && token.getAudience().contains(audience)
                    && token.getExpiresAt() != null && token.getIssuedAt() != null && token.getId() != null
                    && token.getSubject() != null && token.getSubject().matches("[1-9][0-9]{0,18}");
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid token purpose or claims", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(issuer), purpose));
        return decoder;
    }
}
