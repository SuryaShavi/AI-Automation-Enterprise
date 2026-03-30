package com.aieap.platform.auth;

import com.aieap.platform.common.ApiEnvelope;
import com.aieap.platform.common.InputSanitizer;
import com.aieap.platform.common.ResponseFactory;
import com.aieap.platform.common.validation.SafeStringMap;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
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
    private final TokenRevocationStore tokenRevocationStore;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public AuthController(PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
                           ObjectMapper objectMapper, TokenRevocationStore tokenRevocationStore) {
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
        this.tokenRevocationStore = tokenRevocationStore;
        seedUsers();
    }

    // ── Public endpoints ──────────────────────────────────────────────────────

    @PostMapping("/auth/login")
    public ApiEnvelope<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletRequest servletRequest) {
        String email = request.email().toLowerCase().trim();
        ManagedUser user = resolveUserByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        boolean passwordMatches = passwordEncoder.matches(request.password(), user.passwordHash);
        if (!passwordMatches) {
            ManagedUser freshUser = loadUserByEmailFromDatabase(email);
            if (freshUser != null) {
                user = freshUser;
                passwordMatches = passwordEncoder.matches(request.password(), user.passwordHash);
            }
        }

        if (!passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        user.lastLoginAt = Instant.now();
        updateLastLogin(user);
        JwtTokenService.TokenPair pair = jwtTokenService.createTokenPair(user);
        return ResponseFactory.success(servletRequest, new LoginResponse(
            pair.accessToken(), pair.refreshToken(),
            pair.accessTokenExpiresAt(), pair.refreshTokenExpiresAt(),
            toProfile(user)));
    }

    @PostMapping("/auth/refresh")
    public ApiEnvelope<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                               HttpServletRequest servletRequest) {
        Jwt jwt = decodeAndValidate(request.refreshToken(), "refresh");
        tokenRevocationStore.revoke(jwt.getId());
        ManagedUser user = resolveUserByEmail(jwt.getSubject().toLowerCase().trim());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context not found");
        }
        JwtTokenService.TokenPair pair = jwtTokenService.createTokenPair(user);
        return ResponseFactory.success(servletRequest, new LoginResponse(
            pair.accessToken(), pair.refreshToken(),
            pair.accessTokenExpiresAt(), pair.refreshTokenExpiresAt(),
            toProfile(user)));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.OK)
    public ApiEnvelope<Map<String, String>> logout(
        @Valid @RequestBody RefreshRequest request,
        JwtAuthenticationToken authentication,
        HttpServletRequest servletRequest) {
        Jwt refreshJwt = decodeAndValidate(request.refreshToken(), "refresh");
        tokenRevocationStore.revoke(refreshJwt.getId());
        if (authentication != null && authentication.getToken() != null) {
            tokenRevocationStore.revoke(authentication.getToken().getId());
        }
        return ResponseFactory.success(servletRequest, Map.of("status", "revoked"));
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiEnvelope<LoginResponse> registerUser(@Valid @RequestBody RegisterRequest request,
                                                    HttpServletRequest servletRequest) {
        String email = request.email().toLowerCase().trim();
        if (usersByEmail.containsKey(email) || emailExistsInDatabase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        UUID newId = UUID.randomUUID();
        Set<String> newRoles = ConcurrentHashMap.newKeySet();
        newRoles.add("EMPLOYEE");
        ManagedUser newUser = new ManagedUser(
            newId, email,
            InputSanitizer.requiredText(request.firstName()),
            InputSanitizer.requiredText(request.lastName()),
            passwordEncoder.encode(request.password()),
            newRoles,
            new ConcurrentHashMap<>(Map.of("theme", "light", "locale", "en-US")),
            false, Instant.now());
        persistNewUserToDatabase(newUser);
        register(newUser);
        JwtTokenService.TokenPair pair = jwtTokenService.createTokenPair(newUser);
        return ResponseFactory.success(servletRequest, new LoginResponse(
            pair.accessToken(), pair.refreshToken(),
            pair.accessTokenExpiresAt(), pair.refreshTokenExpiresAt(),
            toProfile(newUser)));
    }

    // ── Authenticated endpoints ───────────────────────────────────────────────

    @GetMapping("/auth/me")
    public ApiEnvelope<UserProfile> me(JwtAuthenticationToken authentication,
                                        HttpServletRequest servletRequest) {
        return ResponseFactory.success(servletRequest, toProfile(currentUser(authentication)));
    }

    @GetMapping("/users/me")
    public ApiEnvelope<UserProfile> currentUserProfile(JwtAuthenticationToken authentication,
                                                        HttpServletRequest servletRequest) {
        return ResponseFactory.success(servletRequest, toProfile(currentUser(authentication)));
    }

    @PatchMapping("/users/me")
    @Transactional
    public ApiEnvelope<UserProfile> updateProfile(
        @Valid @RequestBody UpdateProfileRequest request,
        JwtAuthenticationToken authentication,
        HttpServletRequest servletRequest) {
        ManagedUser user = currentUser(authentication);
        String oldFirstName = user.firstName;
        String oldLastName = user.lastName;
        user.firstName = InputSanitizer.requiredText(request.firstName());
        user.lastName = InputSanitizer.requiredText(request.lastName());
        try {
            persistProfileToDatabase(user);
        } catch (RuntimeException ex) {
            user.firstName = oldFirstName;
            user.lastName = oldLastName;
            throw ex;
        }
        return ResponseFactory.success(servletRequest, toProfile(user));
    }

    @PatchMapping("/users/me/password")
    @Transactional
    public ApiEnvelope<Map<String, String>> updatePassword(
        @Valid @RequestBody UpdatePasswordRequest request,
        JwtAuthenticationToken authentication,
        HttpServletRequest servletRequest) {
        ManagedUser user = currentUser(authentication);
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is invalid");
        }
        String oldPasswordHash = user.passwordHash;
        user.passwordHash = passwordEncoder.encode(request.newPassword());
        try {
            persistPasswordToDatabase(user);
        } catch (RuntimeException ex) {
            user.passwordHash = oldPasswordHash;
            throw ex;
        }
        return ResponseFactory.success(servletRequest, Map.of("status", "updated"));
    }

    @PatchMapping("/users/me/preferences")
    @Transactional
    public ApiEnvelope<UserProfile> updatePreferences(
        @Valid @RequestBody UpdatePreferencesRequest request,
        JwtAuthenticationToken authentication,
        HttpServletRequest servletRequest) {
        ManagedUser user = currentUser(authentication);
        Map<String, String> before = new HashMap<>(user.preferences);
        Map<String, String> sanitizedPreferences = InputSanitizer.stringMap(request.preferences());
        try {
            user.preferences.clear();
            user.preferences.putAll(before);
            user.preferences.putAll(sanitizedPreferences);
            persistPreferencesToDatabase(user);
        } catch (RuntimeException ex) {
            user.preferences.clear();
            user.preferences.putAll(before);
            throw ex;
        }
        return ResponseFactory.success(servletRequest, toProfile(user));
    }

    @PostMapping("/users/me/2fa/enable")
    @Transactional
    public ApiEnvelope<Map<String, String>> enableTwoFactor(
        @Valid @RequestBody EnableTwoFactorRequest request,
        JwtAuthenticationToken authentication,
        HttpServletRequest servletRequest) {
        ManagedUser user = currentUser(authentication);
        boolean previous = user.twoFactorEnabled;
        user.twoFactorEnabled = true;
        try {
            persistTwoFactorToDatabase(user);
        } catch (RuntimeException ex) {
            user.twoFactorEnabled = previous;
            throw ex;
        }
        return ResponseFactory.success(servletRequest, Map.of("status", "enabled", "method", request.method()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ManagedUser currentUser(JwtAuthenticationToken authentication) {
        ManagedUser user = usersById.get(authentication.getToken().getClaimAsString("userId"));
        if (user == null) {
            String userIdClaim = authentication.getToken().getClaimAsString("userId");
            if (userIdClaim != null && !userIdClaim.isBlank()) {
                try {
                    user = loadUserByIdFromDatabase(UUID.fromString(userIdClaim));
                } catch (IllegalArgumentException ignored) {
                    // Fall through to unauthorized response.
                }
            }
        }
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context not found");
        }
        return user;
    }

    private Jwt decodeAndValidate(String token, String expectedType) {
        Jwt jwt = jwtTokenService.decode(token);
        if (tokenRevocationStore.isRevoked(jwt.getId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token has been revoked");
        }
        if (!expectedType.equals(jwt.getClaimAsString("type"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token type mismatch");
        }
        return jwt;
    }

    private UserProfile toProfile(ManagedUser user) {
        return new UserProfile(user.id, user.email, user.firstName, user.lastName,
            user.roles, user.preferences, user.twoFactorEnabled, user.lastLoginAt);
    }

    private void seedUsers() {
        loadUsersFromDatabase();
    }

    private ManagedUser resolveUserByEmail(String email) {
        ManagedUser cached = usersByEmail.get(email);
        if (cached != null) {
            return cached;
        }
        return loadUserByEmailFromDatabase(email);
    }

    private boolean emailExistsInDatabase(String email) {
        JdbcTemplate db = requireJdbc();
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM aieap.users WHERE email = ?",
            Integer.class,
            email);
        return count != null && count > 0;
    }

    private void persistNewUserToDatabase(ManagedUser user) {
        JdbcTemplate db = requireJdbc();
        try {
            int inserted = db.update(
                "INSERT INTO aieap.users (id, email, first_name, last_name, password_hash, status, preferences_json, two_factor_enabled) " +
                "VALUES (?::uuid, ?, ?, ?, ?, 'ACTIVE', ?::jsonb, ?) ON CONFLICT (email) DO NOTHING",
                user.id.toString(), user.email, user.firstName, user.lastName, user.passwordHash,
                toJson(user.preferences), user.twoFactorEnabled);
            if (inserted == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
            }
            for (String role : user.roles) {
                db.update(
                    "INSERT INTO aieap.user_roles (user_id, role_id) " +
                    "SELECT ?::uuid, id FROM aieap.roles WHERE code = ? ON CONFLICT (user_id, role_id) DO NOTHING",
                    user.id.toString(), role);
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to persist user", ex);
        }
    }

    private void persistProfileToDatabase(ManagedUser user) {
        JdbcTemplate db = requireJdbc();
        int updated = db.update(
            "UPDATE aieap.users SET first_name = ?, last_name = ?, updated_at = NOW() WHERE id = ?::uuid",
            user.firstName, user.lastName, user.id.toString());
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private void persistPasswordToDatabase(ManagedUser user) {
        JdbcTemplate db = requireJdbc();
        int updated = db.update(
            "UPDATE aieap.users SET password_hash = ?, updated_at = NOW() WHERE id = ?::uuid",
            user.passwordHash, user.id.toString());
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private void persistPreferencesToDatabase(ManagedUser user) {
        JdbcTemplate db = requireJdbc();
        int updated = db.update(
            "UPDATE aieap.users SET preferences_json = ?::jsonb, updated_at = NOW() WHERE id = ?::uuid",
            toJson(user.preferences), user.id.toString());
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private void persistTwoFactorToDatabase(ManagedUser user) {
        JdbcTemplate db = requireJdbc();
        int updated = db.update(
            "UPDATE aieap.users SET two_factor_enabled = ?, updated_at = NOW() WHERE id = ?::uuid",
            user.twoFactorEnabled, user.id.toString());
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private void updateLastLogin(ManagedUser user) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                "UPDATE aieap.users SET last_login_at = ?, updated_at = NOW() WHERE id = ?::uuid",
                Timestamp.from(user.lastLoginAt), user.id.toString());
        } catch (Exception ignored) {
            // Login should still work even if last-login write fails.
        }
    }

    private JdbcTemplate requireJdbc() {
        if (jdbcTemplate == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database is not available");
        }
        return jdbcTemplate;
    }

    private String toJson(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to encode preferences", ex);
        }
    }

    private Map<String, String> parsePreferencesJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(rawJson, new TypeReference<Map<String, String>>() {});
            return parsed != null ? parsed : Map.of();
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private ManagedUser loadUserByEmailFromDatabase(String email) {
        if (jdbcTemplate == null || email == null || email.isBlank()) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                "SELECT u.id, u.email, u.first_name, u.last_name, u.password_hash, " +
                "u.two_factor_enabled, u.last_login_at, u.preferences_json::text AS preferences_json, " +
                "STRING_AGG(r.code, ',') AS roles " +
                "FROM aieap.users u " +
                "LEFT JOIN aieap.user_roles ur ON u.id = ur.user_id " +
                "LEFT JOIN aieap.roles r ON r.id = ur.role_id " +
                "WHERE u.status = 'ACTIVE' AND lower(u.email) = ? " +
                "GROUP BY u.id, u.email, u.first_name, u.last_name, u.password_hash, u.two_factor_enabled, u.last_login_at, u.preferences_json",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    ManagedUser loaded = mapManagedUser(rs);
                    register(loaded);
                    return loaded;
                },
                email.toLowerCase().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private ManagedUser loadUserByIdFromDatabase(UUID userId) {
        if (jdbcTemplate == null || userId == null) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                "SELECT u.id, u.email, u.first_name, u.last_name, u.password_hash, " +
                "u.two_factor_enabled, u.last_login_at, u.preferences_json::text AS preferences_json, " +
                "STRING_AGG(r.code, ',') AS roles " +
                "FROM aieap.users u " +
                "LEFT JOIN aieap.user_roles ur ON u.id = ur.user_id " +
                "LEFT JOIN aieap.roles r ON r.id = ur.role_id " +
                "WHERE u.status = 'ACTIVE' AND u.id = ?::uuid " +
                "GROUP BY u.id, u.email, u.first_name, u.last_name, u.password_hash, u.two_factor_enabled, u.last_login_at, u.preferences_json",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    ManagedUser loaded = mapManagedUser(rs);
                    register(loaded);
                    return loaded;
                },
                userId.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private ManagedUser mapManagedUser(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        String email = Objects.requireNonNullElse(rs.getString("email"), "").toLowerCase();
        String firstName = rs.getString("first_name");
        String lastName = rs.getString("last_name");
        String passwordHash = rs.getString("password_hash");
        boolean twoFactorEnabled = rs.getBoolean("two_factor_enabled");
        Timestamp lastLoginTs = rs.getTimestamp("last_login_at");
        Instant lastLoginAt = lastLoginTs != null ? lastLoginTs.toInstant() : Instant.now();
        String rolesStr = rs.getString("roles");
        Set<String> roles = ConcurrentHashMap.newKeySet();
        if (rolesStr != null && !rolesStr.isBlank()) {
            roles.addAll(Arrays.asList(rolesStr.split(",")));
        }
        if (roles.isEmpty()) {
            roles.add("EMPLOYEE");
        }
        Map<String, String> preferences = parsePreferencesJson(rs.getString("preferences_json"));
        return new ManagedUser(id, email, firstName, lastName, passwordHash,
            roles, new ConcurrentHashMap<>(preferences), twoFactorEnabled, lastLoginAt);
    }

    private boolean loadUsersFromDatabase() {
        if (jdbcTemplate == null) return false;
        try {
            jdbcTemplate.query(
                "SELECT u.id, u.email, u.first_name, u.last_name, u.password_hash, " +
                "u.two_factor_enabled, u.last_login_at, u.preferences_json::text AS preferences_json, " +
                "STRING_AGG(r.code, ',') AS roles " +
                "FROM aieap.users u " +
                "LEFT JOIN aieap.user_roles ur ON u.id = ur.user_id " +
                "LEFT JOIN aieap.roles r ON r.id = ur.role_id " +
                "WHERE u.status = 'ACTIVE' " +
                "GROUP BY u.id, u.email, u.first_name, u.last_name, u.password_hash, u.two_factor_enabled, u.last_login_at, u.preferences_json",
                (ResultSetExtractor<Void>) rs -> {
                    while (rs.next()) {
                        ManagedUser dbUser = mapManagedUser(rs);
                        register(dbUser);
                    }
                    return null;
                });
            return !usersByEmail.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void register(ManagedUser user) {
        usersByEmail.put(user.email, user);
        usersById.put(user.id.toString(), user);
    }

    // ── Inner types ───────────────────────────────────────────────────────────

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

        public ManagedUser(UUID id, String email, String firstName, String lastName,
                           String passwordHash, Set<String> roles,
                           ConcurrentHashMap<String, String> preferences,
                           boolean twoFactorEnabled, Instant lastLoginAt) {
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

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$",
                 message = "Password must be 8-72 characters and include uppercase, lowercase, and a digit")
        @NotBlank String password) {}

    public record LoginResponse(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        UserProfile user) {}

    public record UserProfile(
        UUID id, String email, String firstName, String lastName,
        Set<String> roles, Map<String, String> preferences,
        boolean twoFactorEnabled, Instant lastLoginAt) {}

    public record UpdateProfileRequest(@NotBlank String firstName, @NotBlank String lastName) {}

    public record UpdatePasswordRequest(
        @NotBlank String currentPassword,
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\w\\s]).{12,72}$",
                 message = "Password must be 12-72 chars and include upper, lower, digit, and symbol")
        String newPassword) {}

    public record UpdatePreferencesRequest(@SafeStringMap Map<String, String> preferences) {
        public UpdatePreferencesRequest {
            preferences = preferences == null ? Map.of() : preferences;
        }
    }

    public record EnableTwoFactorRequest(@NotBlank String method) {}
}
