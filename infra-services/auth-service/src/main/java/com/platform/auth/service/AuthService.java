package com.platform.auth.service;

import com.platform.auth.dto.request.ForgotPasswordRequest;
import com.platform.auth.dto.response.UserCreditInternalResponse;
import com.platform.auth.dto.request.LoginRequest;
import com.platform.auth.dto.request.RefreshTokenRequest;
import com.platform.auth.dto.request.RegisterRequest;
import com.platform.auth.dto.response.AuthTokenResponse;
import com.platform.auth.dto.response.UserResponse;
import com.platform.auth.entity.User;
import com.platform.auth.entity.UserStatus;
import org.springframework.data.domain.Page;
import com.platform.auth.exception.AuthErrorCode;
import com.platform.auth.repository.UserRepository;
import com.platform.auth.util.JwtTokenProvider;
import com.platform.common.core.exception.BaseException;
import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.event.UserPasswordResetEvent;
import com.platform.common.rmq.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RabbitTemplate rabbitTemplate;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       RabbitTemplate rabbitTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BaseException(AuthErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
        }

        User user = new User(null, request.email(), passwordEncoder.encode(request.password()), Set.of("ADMIN"));
        User saved = userRepository.save(user);
        rabbitTemplate.convertAndSend(
                RmqExchanges.USER_EVENTS,
                RmqRoutingKeys.USER_REGISTERED,
                new UserRegisteredEvent(saved.getId(), saved.getEmail(), saved.getMsisdn(), Instant.now().toEpochMilli())
        );
        return issueTokens(saved);
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BaseException(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
        ensureActive(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BaseException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        if (!jwtTokenProvider.isValid(request.refreshToken())) {
            throw new BaseException(AuthErrorCode.AUTH_TOKEN_REFRESH_FAILED);
        }
        Long userId = Long.valueOf(jwtTokenProvider.parse(request.refreshToken()).getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(AuthErrorCode.AUTH_USER_NOT_FOUND));
        ensureActive(user);
        return issueTokens(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BaseException(AuthErrorCode.AUTH_USER_NOT_FOUND));
        String otp = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        rabbitTemplate.convertAndSend(
                RmqExchanges.USER_EVENTS,
                RmqRoutingKeys.USER_PASSWORD_RESET,
                new UserPasswordResetEvent(user.getId(), user.getEmail(), otp, Instant.now().toEpochMilli())
        );
    }

    @Transactional
    public User lazyCreateSubscriber(String msisdn) {
        return userRepository.findByMsisdn(msisdn).orElseGet(() -> {
            log.info("[CRBT] New subscriber, auto-creating account msisdn={}", mask(msisdn));
            User user = new User(msisdn, null, null, Set.of("USER"));
            User saved = userRepository.save(user);
            rabbitTemplate.convertAndSend(
                    RmqExchanges.USER_EVENTS,
                    RmqRoutingKeys.USER_REGISTERED,
                    new UserRegisteredEvent(saved.getId(), saved.getEmail(), saved.getMsisdn(), Instant.now().toEpochMilli())
            );
            log.info("[CRBT] Auto-created userId={} msisdn={}", saved.getId(), mask(msisdn));
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public UserCreditInternalResponse getUserCredit(String msisdn) {
        User user = userRepository.findByMsisdn(msisdn)
                .orElseThrow(() -> new BaseException(AuthErrorCode.AUTH_USER_NOT_FOUND));
        return new UserCreditInternalResponse(user.getId(), user.getMsisdn());
    }

    private UserStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }
        if ("deactive".equalsIgnoreCase(statusStr) || "inactive".equalsIgnoreCase(statusStr)) {
            return UserStatus.LOCKED;
        }
        try {
            return UserStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private java.time.Instant parseInstant(String tsStr, boolean isEndOfDay) {
        if (tsStr == null || tsStr.isBlank()) {
            return null;
        }
        try {
            tsStr = tsStr.trim();
            if (tsStr.matches("^\\d+$")) {
                return java.time.Instant.ofEpochMilli(Long.parseLong(tsStr));
            }
            if (tsStr.endsWith("Z") || tsStr.contains("+") || (tsStr.contains("-") && tsStr.lastIndexOf("-") > 7 && tsStr.contains("T") && (tsStr.contains(":") && (tsStr.contains("+") || tsStr.substring(tsStr.lastIndexOf(":")).contains("-"))))) {
                return java.time.Instant.parse(tsStr);
            }
            if (tsStr.contains(" ") && !tsStr.contains("T")) {
                tsStr = tsStr.replace(" ", "T");
            }
            if (tsStr.contains("T")) {
                return java.time.LocalDateTime.parse(tsStr).atZone(java.time.ZoneId.systemDefault()).toInstant();
            } else {
                java.time.LocalDate date = java.time.LocalDate.parse(tsStr);
                if (isEndOfDay) {
                    return date.atTime(23, 59, 59, 999999999).atZone(java.time.ZoneId.systemDefault()).toInstant();
                } else {
                    return date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
                }
            }
        } catch (Exception e) {
            throw new BaseException(com.platform.common.core.exception.CommonErrorCode.COMMON_BAD_REQUEST, "Invalid timestamp format: " + tsStr);
        }
    }

    @Transactional(readOnly = true)
    public com.platform.common.core.response.PageResponse<UserResponse> searchUsers(
            String msisdn, String statusStr, String startTimeStr, String endTimeStr, org.springframework.data.domain.Pageable pageable) {
        UserStatus status = parseStatus(statusStr);
        java.time.Instant startTime = parseInstant(startTimeStr, false);
        java.time.Instant endTime = parseInstant(endTimeStr, true);
        Page<User> page = userRepository.searchUsers(msisdn, status, startTime, endTime, pageable);
        return com.platform.common.core.response.PageResponse.from(page.map(u -> new UserResponse(
                u.getId(),
                u.getMsisdn(),
                u.getEmail(),
                u.getStatus().name(),
                u.getCreatedAt() != null ? u.getCreatedAt().toEpochMilli() : null
        )));
    }

    @Transactional(readOnly = true)
    public List<Long> searchUserIds(String msisdn, String statusStr, String startTimeStr, String endTimeStr) {
        UserStatus status = parseStatus(statusStr);
        java.time.Instant startTime = parseInstant(startTimeStr, false);
        java.time.Instant endTime = parseInstant(endTimeStr, true);
        return userRepository.searchUserIds(msisdn, status, startTime, endTime);
    }

    private String mask(String msisdn) {
        if (msisdn == null || msisdn.length() <= 4) return "***";
        return msisdn.substring(0, 3) + "***" + msisdn.substring(msisdn.length() - 2);
    }


    @Transactional(readOnly = true)
    public java.util.Map<Long, String> getMsisdnsByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .filter(u -> u.getMsisdn() != null)
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getMsisdn));
    }

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(AuthErrorCode.AUTH_ACCOUNT_LOCKED);
        }
    }

    private AuthTokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRoles());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail(), user.getRoles());
        return AuthTokenResponse.of(accessToken, refreshToken, jwtTokenProvider.accessTokenTtlSeconds());
    }
}
