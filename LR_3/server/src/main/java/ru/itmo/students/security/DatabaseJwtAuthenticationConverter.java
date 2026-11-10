package ru.itmo.students.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import ru.itmo.students.repository.UserRepository;
import java.util.List;

@Component
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository users;
    public DatabaseJwtAuthenticationConverter(UserRepository users) { this.users = users; }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long id;
        try { id = Long.valueOf(jwt.getSubject()); }
        catch (NumberFormatException exception) { throw invalid(); }
        var user = users.findById(id).orElseThrow(this::invalid);
        // The current database role is authoritative, even if a previously signed role claim is stale.
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())), user.getUsername());
    }

    private OAuth2AuthenticationException invalid() { return new OAuth2AuthenticationException(new OAuth2Error("invalid_token")); }
}
