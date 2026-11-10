package ru.itmo.students.service;

import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.students.config.JwtProperties;
import ru.itmo.students.dto.*;
import ru.itmo.students.entity.*;
import ru.itmo.students.exception.ApiException;
import ru.itmo.students.repository.*;
import ru.itmo.students.security.JwtTokens;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    private final JwtProperties properties;
    private final Clock clock;
    private final Validator validator;
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwords,
                       JwtTokens tokens, JwtProperties properties, Clock clock, Validator validator) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
        this.validator = validator;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public UserResponse register(RegistrationRequest request) { return UserResponse.from(createUser(request, Role.USER)); }

    public TokenResponse login(LoginRequest request) {
        var user = users.findByUsernameIgnoreCase(request.username()).orElse(null);
        boolean fits = request.password().getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean valid = fits && passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !valid) throw new ApiException(HttpStatus.UNAUTHORIZED, "Неверное имя пользователя или пароль");
        return newSession(user);
    }

    public TokenResponse refresh(String raw) {
        var stored = checkedRefresh(raw);
        stored.revoke(now());
        // Revocation and replacement are committed atomically, with the old row locked.
        var response = newSession(stored.getUser());
        refreshTokens.flush();
        return response;
    }

    public void logout(String raw) {
        var stored = checkedRefresh(raw);
        stored.revoke(now());
        refreshTokens.flush();
    }

    @Transactional(readOnly = true)
    public UserResponse me(long id) {
        return UserResponse.from(users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Требуется авторизация")));
    }

    /** Called only by the optional startup bootstrap, never exposed through REST. */
    public void provisionAdmin(RegistrationRequest request) {
        var existing = users.findByUsernameIgnoreCase(request.username());
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN || !existing.get().getEmail().equalsIgnoreCase(request.email())) {
                throw new IllegalStateException("Bootstrap admin conflicts with an existing account; no role or password was changed");
            }
            return;
        }
        createUser(request, Role.ADMIN);
    }

    private UserEntity createUser(RegistrationRequest request, Role role) {
        var errors = new LinkedHashMap<String, Object>();
        validator.validate(request).forEach(error -> errors.putIfAbsent(error.getPropertyPath().toString(), error.getMessage()));
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Ошибка валидации", errors);
        if (users.existsByUsernameIgnoreCase(request.username())) errors.put("username", "Имя пользователя уже занято.");
        if (users.existsByEmailIgnoreCase(request.email())) errors.put("email", "Email уже используется.");
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "Пользователь уже существует", errors);
        return users.saveAndFlush(new UserEntity(request.username(), request.email().toLowerCase(Locale.ROOT),
                passwords.encode(request.password()), role, now()));
    }

    private TokenResponse newSession(UserEntity user) {
        Instant created = now();
        UUID id = UUID.randomUUID();
        var response = tokens.issue(user, id, created);
        refreshTokens.saveAndFlush(new RefreshTokenEntity(id, user, JwtTokens.hash(response.refreshToken()),
                created, created.plus(properties.refreshTtl())));
        return response;
    }

    private RefreshTokenEntity checkedRefresh(String raw) {
        var decoded = tokens.decodeRefresh(raw);
        UUID id;
        try { id = UUID.fromString(decoded.getId()); }
        catch (IllegalArgumentException exception) { throw tokens.invalidRefresh(); }
        var stored = refreshTokens.findByIdForUpdate(id).orElseThrow(tokens::invalidRefresh);
        if (stored.getRevokedAt() != null || !stored.getExpiresAt().isAfter(now())
                || !stored.getUser().getId().toString().equals(decoded.getSubject())
                || !MessageDigest.isEqual(stored.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                        JwtTokens.hash(raw).getBytes(StandardCharsets.US_ASCII))) throw tokens.invalidRefresh();
        return stored;
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.SECONDS); }
}
