package com.platform.auth.service;

import com.platform.auth.config.JwtProperties;
import com.platform.auth.dto.request.ForgotPasswordRequest;
import com.platform.auth.dto.request.LoginRequest;
import com.platform.auth.dto.request.RefreshTokenRequest;
import com.platform.auth.dto.request.RegisterRequest;
import com.platform.auth.dto.response.AuthTokenResponse;
import com.platform.auth.entity.User;
import com.platform.auth.entity.UserStatus;
import com.platform.auth.exception.AuthErrorCode;
import com.platform.auth.repository.UserRepository;
import com.platform.auth.util.JwtTokenProvider;
import com.platform.common.core.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RabbitTemplate rabbitTemplate;

    private AuthService authService;
    private JwtTokenProvider jwtTokenProvider;
    private PasswordEncoder passwordEncoder;

    private static final String SECRET =
            "test-secret-that-is-at-least-256-bits-long-padded-string-here";

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SECRET, 3_600_000L, 86_400_000L);
        jwtTokenProvider = new JwtTokenProvider(props);
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, rabbitTemplate);
    }

    // --- register ---

    @Test
    void register_newEmail_returnsTokens() {
        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        User saved = makeUser(1L, null, "admin@test.com", "ADMIN");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        AuthTokenResponse response = authService.register(new RegisterRequest("admin@test.com", "P@ssword1"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void register_duplicateEmail_throws() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("dup@test.com", "P@ssword1")))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_EMAIL_ALREADY_EXISTS));
    }

    // --- login ---

    @Test
    void login_correctCredentials_returnsTokens() {
        User user = makeUser(2L, null, "user@test.com", "ADMIN");
        user.setPasswordHash(passwordEncoder.encode("P@ssword1"));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        AuthTokenResponse response = authService.login(new LoginRequest("user@test.com", "P@ssword1"));

        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    void login_wrongPassword_throws() {
        User user = makeUser(2L, null, "user@test.com", "ADMIN");
        user.setPasswordHash(passwordEncoder.encode("correctPass"));
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "wrongPass")))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
    }

    @Test
    void login_unknownEmail_throws() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@test.com", "pass")))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
    }

    @Test
    void login_lockedAccount_throws() {
        User user = makeUser(3L, null, "locked@test.com", "ADMIN");
        user.setStatus(UserStatus.LOCKED);
        user.setPasswordHash(passwordEncoder.encode("pass"));
        when(userRepository.findByEmail("locked@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("locked@test.com", "pass")))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_ACCOUNT_LOCKED));
    }

    // --- refresh ---

    @Test
    void refresh_validToken_returnsNewTokens() {
        User user = makeUser(5L, null, "a@b.com", "USER");
        String refreshToken = jwtTokenProvider.generateRefreshToken(5L, "a@b.com", Set.of("USER"));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        AuthTokenResponse response = authService.refresh(new RefreshTokenRequest(refreshToken));

        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    void refresh_invalidToken_throws() {
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("bad.token.here")))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_TOKEN_REFRESH_FAILED));
    }

    // --- forgotPassword ---

    @Test
    void forgotPassword_knownEmail_publishesEvent() {
        User user = makeUser(6L, null, "known@test.com", "ADMIN");
        when(userRepository.findByEmail("known@test.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(new ForgotPasswordRequest("known@test.com"));

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void forgotPassword_unknownEmail_throws() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.forgotPassword(new ForgotPasswordRequest("ghost@test.com")))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_USER_NOT_FOUND));
    }

    // --- lazyCreateSubscriber ---

    @Test
    void lazyCreateSubscriber_existingMsisdn_returnsExisting() {
        User existing = makeUser(7L, "+959123456789", null, "USER");
        when(userRepository.findByMsisdn("+959123456789")).thenReturn(Optional.of(existing));

        User result = authService.lazyCreateSubscriber("+959123456789");

        assertThat(result.getId()).isEqualTo(7L);
        verify(userRepository, never()).save(any());
    }

    @Test
    void lazyCreateSubscriber_newMsisdn_createsWithTrialCredit() {
        when(userRepository.findByMsisdn("+959000000001")).thenReturn(Optional.empty());
        User created = makeUser(8L, "+959000000001", null, "USER");
        when(userRepository.save(any(User.class))).thenReturn(created);

        User result = authService.lazyCreateSubscriber("+959000000001");

        assertThat(result.getId()).isEqualTo(8L);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    // --- helpers ---

    private User makeUser(Long id, String msisdn, String email, String role) {
        User u = new User(msisdn, email, null, Set.of(role));
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception ignored) {
        }
        return u;
    }
}
