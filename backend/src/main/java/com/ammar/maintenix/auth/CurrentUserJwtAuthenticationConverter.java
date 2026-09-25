package com.ammar.maintenix.auth;

import com.ammar.maintenix.user.User;
import com.ammar.maintenix.user.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CurrentUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public CurrentUserJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Spring Security has already verified the signature and token timestamps.
        // Identity is stable even if an email changes or is reused by another account.
        Object claim = jwt.getClaims().get("userId");
        if (!(claim instanceof String userId)) {
            throw new InvalidBearerTokenException("Invalid access token");
        }
        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidBearerTokenException("Invalid access token");
        }
        User user = userRepository.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidBearerTokenException("Invalid access token"));

        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                user.getEmail());
    }
}
