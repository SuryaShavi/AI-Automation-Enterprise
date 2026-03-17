package com.aieap.platform.auth;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.ResponseFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "Auth")
@RequestMapping
public class AuthController {
    private final ConcurrentHashMap<String, ManagedUser> usersByEmail = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ManagedUser> usersById = new ConcurrentHashMap<>();
    private final Set<String> revokedTokenIds = ConcurrentHashMap.newKeySet();
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthController(PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        seedUsers();
    }

    @PostMapping("/auth/login")
    public ApiEnvelope<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        ManagedUser user = usersByEmail.get(request.email().toLowerCase());
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        user.lastLoginAt = Instant.now();
        JwtTokenService.TokenPair tokenPair = jwtTokenService.createTokenPair(user);
        return ResponseFactory.success(servletRequest, new LoginResponse(
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            tokenPair.accessTokenExpiresAt(),
            tokenPair.refreshTokenExpiresAt(),
            toProfile(user)
        ));
    }

    @PostMapping("/auth/refresh")
    public ApiEnvelope<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        Jwt jwt = decodeAndValidate(request.refreshToken(), "refresh");
        revokedTokenIds.add(jwt.getId());

        ManagedUser user = usersByEmail.get(jwt.getSubject().toLowerCase());
        JwtTokenService.TokenPair tokenPair = jwtTokenService.createTokenPair(user);
        return ResponseFactory.success(servletRequest, new LoginResponse(
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            tokenPair.accessTokenExpiresAt(),
            tokenPair.refreshTokenExpiresAt(),
            toProfile(user)
        ));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.OK)
    public ApiEnvelope<Map<String, String>> logout(
        @Valid @RequestBody RefreshRequest request,
        JwtAuthenticationToken authentication,
        HttpServletRequest servletRequest
    ) {
        Jwt refreshJwt = decodeAndValidate(request.refreshToken(), "refresh");
        revokedTokenIds.add(refreshJwt.getId());
        if (authentication != null && authentication.getToken() != null) {
            revokedTokenIds.add(authentication.getToken().getId());
        }
        return ResponseFactory.success(servletRequest, Map.of("status", "revoked"));
    }

    @GetMapping("/auth/me")
    public ApiEnvelope<UserProfile> me(JwtAuthenticationToken authentication, HttpServletRequest servletRequest) {
        return ResponseFactory.success(servletRequest, toProfile(currentUser(authentication)));
    }

    @GetMapping("/users/me")
    public ApiEnvelope<UserProfile> currentUserProfile(JwtAuthenticationToken authentication, HttpServletRequest servletRequest) {
        return ResponseFactory.success(servletRequest, toProfile(currentUser(authentication)));
    }

    @PatchMapping("/users/me")
    public ApiEnvelope<UserProfile> updateProfile(
        @Valid @RequestBody UpdateProfileRequest request,
        JwtAuthenticationToken authentication,
        HttpServletRequest servletRequest
    ) {
        ManagedUser user = currentUser(authentication);
        user.firstName = request.firstName();
        user.lastName = request.lastName();
        return ResponseFactory.success(servletRequest, toProfile(user));
    }

    @PatchMapping("/users/me/password")
    public ApiEnvelope<Map<String, String>> updatePassword(
        @Valid @RequestBody UpdatePasswordRequest request,
        JwtAuthenticationToken authentication,
        HttpServletRequest servletRequest
    ) {
        ManagedUser user = currentUser(authentication);
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is invalid");
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword());
        return ResponseFactory.success(servletRequest, Map.of("status", "updated"));
    }

    @PatchMapping("/users/me/preferences")
    public ApiEnvelope<UserProfile> updatePreferences(
        @Valid @RequestBody UpdatePreferencesRequest request,
        JwtAuthenticationToken authentication,
        HttpServletRequest servletRequest
    ) {
        ManagedUser user = currentUser(authentication);
        user.preferences.putAll(request.preferences());
        return ResponseFactory.success(servletRequest, toProfile(user));
    }

    @PostMapping("/users/me/2fa/enable")
    public ApiEnvelope<Map<String, String>> enableTwoFactor(
        @Valid @RequestBody EnableTwoFactorRequest request,
        JwtAuthenticationToken authentication,
        HttpServletRequest servletRequest
    ) {
        ManagedUser user = currentUser(authentication);
        user.twoFactorEnabled = true;
        return ResponseFactory.success(servletRequest, Map.of("status", "enabled", "method", request.method()));
    }

    private ManagedUser currentUser(JwtAuthenticationToken authentication) {
        ManagedUser user = usersById.get(authentication.getToken().getClaimAsString("userId"));
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context not found");
        }
        return user;
    }

    private Jwt decodeAndValidate(String token, String expectedType) {
        Jwt jwt = jwtTokenService.decode(token);
        if (revokedTokenIds.contains(jwt.getId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token has been revoked");
        }
        if (!expectedType.equals(jwt.getClaimAsString("type"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token type mismatch");
        }
        return jwt;
    }

    private UserProfile toProfile(ManagedUser user) {
        return new UserProfile(user.id, user.email, user.firstName, user.lastName, user.roles, user.preferences, user.twoFactorEnabled, user.lastLoginAt);
    }

    private void seedUsers() {
        register(new ManagedUser(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "admin@aieap.local",
            "Ava",
            "Admin",
            passwordEncoder.encode("ChangeMe123!"),
            Set.of("ADMIN"),
            new ConcurrentHashMap<>(Map.of("theme", "light", "locale", "en-IN")),
            true,
            Instant.now()
        ));

        register(new ManagedUser(
            UUID.fromString("10000000-0000-0000-0000-000000000002"),
            "employee@aieap.local",
            "Evan",
            "Employee",
            passwordEncoder.encode("ChangeMe123!"),
            Set.of("EMPLOYEE"),
            new ConcurrentHashMap<>(Map.of("theme", "light", "locale", "en-US")),
            false,
            Instant.now()
        ));
    }

    private void register(ManagedUser user) {
        usersByEmail.put(user.email, user);
        usersById.put(user.id.toString(), user);
    }

    public static final class ManagedUser {
        final UUID id;
        final String email;
        String firstName;
        String lastName;
        String passwordHash;
        final Set<String> roles;
        final ConcurrentHashMap<String, String> preferences;
        boolean twoFactorEnabled;
        Instant lastLoginAt;

        public ManagedUser(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String passwordHash,
            Set<String> roles,
            ConcurrentHashMap<String, String> preferences,
            boolean twoFactorEnabled,
            Instant lastLoginAt
        ) {
            this.id = id;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.passwordHash = passwordHash;
            this.roles = roles;
            this.preferences = preferences;
            this.twoFactorEnabled = twoFactorEnabled;
            this.lastLoginAt = lastLoginAt;
        }
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LoginResponse(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        UserProfile user
    ) {
    }

    public record UserProfile(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Set<String> roles,
        Map<String, String> preferences,
        boolean twoFactorEnabled,
        Instant lastLoginAt
    ) {
    }

    public record UpdateProfileRequest(@NotBlank String firstName, @NotBlank String lastName) {
    }

    public record UpdatePasswordRequest(
        @NotBlank String currentPassword,
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\w\\s]).{12,72}$", message = "Password must be 12-72 chars and include upper, lower, digit, and symbol")
        String newPassword
    ) {
    }

    public record UpdatePreferencesRequest(Map<String, String> preferences) {
    }

    public record EnableTwoFactorRequest(@NotBlank String method) {
    }
}