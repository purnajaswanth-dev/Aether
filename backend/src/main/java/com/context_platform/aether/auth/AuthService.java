package com.context_platform.aether.auth;

import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.event.AuditService;
import com.context_platform.aether.security.JwtTokenProvider;
import com.context_platform.aether.security.LoginRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginRateLimiter rateLimiter;
    private final AuditService auditService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider tokenProvider,
        LoginRateLimiter rateLimiter,
        AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException("An account with email " + request.email() + " already exists", HttpStatus.CONFLICT);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = userRepository.create(request.email(), passwordHash, request.displayName());

        auditService.log(null, user.id(), "REGISTER", "HUMAN_USER", ipAddress, userAgent, "{\"email\":\"" + user.email() + "\"}");

        String token = tokenProvider.generateToken(user.id(), user.email(), user.displayName());
        return new AuthResponse(UserDto.from(user), token);
    }

    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String rateLimitKey = ipAddress + ":" + request.email().toLowerCase().trim();
        if (rateLimiter.isBlocked(rateLimitKey)) {
            throw new ApiException("Too many failed login attempts. Please wait 5 minutes before trying again.", HttpStatus.TOO_MANY_REQUESTS);
        }

        User user = userRepository.findByEmail(request.email())
            .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            rateLimiter.recordFailure(rateLimitKey);
            auditService.log(null, null, "LOGIN_FAILED", "HUMAN_USER", ipAddress, userAgent, "{\"email\":\"" + request.email() + "\"}");
            throw new ApiException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        rateLimiter.recordSuccess(rateLimitKey);
        auditService.log(null, user.id(), "LOGIN_SUCCESS", "HUMAN_USER", ipAddress, userAgent, "{\"email\":\"" + user.email() + "\"}");

        String token = tokenProvider.generateToken(user.id(), user.email(), user.displayName());
        return new AuthResponse(UserDto.from(user), token);
    }

    public UserDto getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        return UserDto.from(user);
    }
}

