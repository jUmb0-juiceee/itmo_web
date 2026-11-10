package ru.itmo.students.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import ru.itmo.students.config.JwtProperties;
import ru.itmo.students.dto.*;
import ru.itmo.students.entity.UserEntity;
import ru.itmo.students.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokens {
    private final JwtEncoder encoder;
    private final JwtDecoder refreshDecoder;
    private final JwtProperties properties;

    public JwtTokens(JwtEncoder encoder, @Qualifier("refreshJwtDecoder") JwtDecoder refreshDecoder, JwtProperties properties) {
        this.encoder = encoder;
        this.refreshDecoder = refreshDecoder;
        this.properties = properties;
    }

    public TokenResponse issue(UserEntity user, UUID refreshId, Instant now) {
        var access = claims(user, UUID.randomUUID().toString(), now, now.plus(properties.accessTtl()), "access", properties.audience())
                .claim("username", user.getUsername()).claim("role", user.getRole().name()).build();
        var refresh = claims(user, refreshId.toString(), now, now.plus(properties.refreshTtl()), "refresh", properties.refreshAudience()).build();
        return new TokenResponse(encode(access), encode(refresh), "Bearer", properties.accessTtl().toSeconds(),
                properties.refreshTtl().toSeconds(), UserResponse.from(user));
    }

    public Jwt decodeRefresh(String token) {
        try { return refreshDecoder.decode(token); }
        catch (JwtException | IllegalArgumentException exception) { throw invalidRefresh(); }
    }

    public ApiException invalidRefresh() { return new ApiException(HttpStatus.UNAUTHORIZED, "Refresh-токен недействителен или истёк"); }

    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private JwtClaimsSet.Builder claims(UserEntity user, String id, Instant now, Instant expires, String type, String audience) {
        return JwtClaimsSet.builder().issuer(properties.issuer()).subject(user.getId().toString()).id(id)
                .issuedAt(now).notBefore(now).expiresAt(expires).audience(List.of(audience)).claim("token_use", type);
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }
}
