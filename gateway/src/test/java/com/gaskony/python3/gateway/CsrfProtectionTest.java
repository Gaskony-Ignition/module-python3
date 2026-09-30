package com.gaskony.python3.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CsrfProtection.
 *
 * Covers: token generation, validation, expiry, cleanup, secureEquals.
 * SDK dependency is only on CsrfProtection.validateToken/validateIfSession (RequestContext).
 * Pure logic methods (secureEquals, cleanupExpired, generateToken) need no mocking.
 */
@ExtendWith(MockitoExtension.class)
class CsrfProtectionTest {

    private CsrfProtection csrf;

    @Mock
    private RequestContext req;
    @Mock
    private HttpServletRequest httpReq;
    @Mock
    private HttpSession session;

    @BeforeEach
    void setUp() {
        csrf = new CsrfProtection();
    }

    // ===== Token generation =====

    @Test
    void generateToken_returnsNonNullUuid() {
        String token = csrf.generateToken("session1");
        assertThat(token).isNotNull().isNotBlank();
        // UUID format: 8-4-4-4-12
        assertThat(token).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void generateToken_storesInMap() {
        String token = csrf.generateToken("session1");
        assertThat(csrf.csrfTokens).containsKey("session1");
        assertThat(csrf.csrfTokens.get("session1")).isEqualTo(token);
    }

    @Test
    void generateToken_storesTimestamp() {
        long before = System.currentTimeMillis();
        csrf.generateToken("session1");
        long after = System.currentTimeMillis();

        assertThat(csrf.csrfTokenTimestamps).containsKey("session1");
        long ts = csrf.csrfTokenTimestamps.get("session1");
        assertThat(ts).isBetween(before, after);
    }

    @Test
    void generateToken_differentSessionsDifferentTokens() {
        String t1 = csrf.generateToken("session1");
        String t2 = csrf.generateToken("session2");
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void generateToken_sameSessionOverwrites() {
        String t1 = csrf.generateToken("session1");
        String t2 = csrf.generateToken("session1");
        // Second call replaces first
        assertThat(csrf.csrfTokens.get("session1")).isEqualTo(t2);
        assertThat(t1).isNotEqualTo(t2);
    }

    // ===== Token validation =====

    @Test
    void validateToken_returnsTrueForValidToken() {
        csrf.csrfTokens.put("sess1", "mytoken");
        csrf.csrfTokenTimestamps.put("sess1", System.currentTimeMillis());

        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getSession(false)).thenReturn(session);
        when(session.getId()).thenReturn("sess1");
        when(httpReq.getHeader("X-CSRF-Token")).thenReturn("mytoken");

        assertThat(csrf.validateToken(req)).isTrue();
    }

    @Test
    void validateToken_returnsFalseWhenNoSession() {
        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getSession(false)).thenReturn(null);

        assertThat(csrf.validateToken(req)).isFalse();
    }

    @Test
    void validateToken_returnsFalseWhenNoHeader() {
        csrf.csrfTokens.put("sess1", "mytoken");
        csrf.csrfTokenTimestamps.put("sess1", System.currentTimeMillis());

        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getSession(false)).thenReturn(session);
        when(session.getId()).thenReturn("sess1");
        when(httpReq.getHeader("X-CSRF-Token")).thenReturn(null);

        assertThat(csrf.validateToken(req)).isFalse();
    }

    @Test
    void validateToken_returnsFalseWhenNoStoredToken() {
        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getSession(false)).thenReturn(session);
        when(session.getId()).thenReturn("unknownSession");
        when(httpReq.getHeader("X-CSRF-Token")).thenReturn("sometoken");

        assertThat(csrf.validateToken(req)).isFalse();
    }

    @Test
    void validateToken_returnsFalseOnMismatch() {
        csrf.csrfTokens.put("sess1", "expected");
        csrf.csrfTokenTimestamps.put("sess1", System.currentTimeMillis());

        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getSession(false)).thenReturn(session);
        when(session.getId()).thenReturn("sess1");
        when(httpReq.getHeader("X-CSRF-Token")).thenReturn("wrong");

        assertThat(csrf.validateToken(req)).isFalse();
    }

    // ===== validateIfSession =====

    @Test
    void validateIfSession_skipsBearerToken() {
        // Should not throw even with no stored CSRF token
        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getHeader("Authorization")).thenReturn("Bearer sometoken");

        assertThatCode(() -> csrf.validateIfSession(req)).doesNotThrowAnyException();
    }

    @Test
    void validateIfSession_skipsWhenNoSession() {
        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getHeader("Authorization")).thenReturn(null);
        when(httpReq.getSession(false)).thenReturn(null);

        assertThatCode(() -> csrf.validateIfSession(req)).doesNotThrowAnyException();
    }

    @Test
    void validateIfSession_throwsWhenSessionPresentAndTokenMissing() {
        csrf.csrfTokens.put("sess1", "correct");
        csrf.csrfTokenTimestamps.put("sess1", System.currentTimeMillis());

        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getHeader("Authorization")).thenReturn(null);
        when(httpReq.getSession(false)).thenReturn(session);
        when(session.getId()).thenReturn("sess1");
        when(httpReq.getHeader("X-CSRF-Token")).thenReturn(null);

        assertThatThrownBy(() -> csrf.validateIfSession(req))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("CSRF");
    }

    @Test
    void validateIfSession_noThrowWhenSessionPresentAndTokenValid() {
        csrf.csrfTokens.put("sess1", "correct");
        csrf.csrfTokenTimestamps.put("sess1", System.currentTimeMillis());

        when(req.getRequest()).thenReturn(httpReq);
        when(httpReq.getHeader("Authorization")).thenReturn(null);
        when(httpReq.getSession(false)).thenReturn(session);
        when(session.getId()).thenReturn("sess1");
        when(httpReq.getHeader("X-CSRF-Token")).thenReturn("correct");

        assertThatCode(() -> csrf.validateIfSession(req)).doesNotThrowAnyException();
    }

    // ===== Cleanup =====

    @Test
    void cleanupExpired_removesExpiredEntries() throws InterruptedException {
        csrf.csrfTokens.put("old", "token");
        // Put a timestamp that is already expired (8 hours + 1 ms ago)
        csrf.csrfTokenTimestamps.put("old", System.currentTimeMillis() - CsrfProtection.CSRF_TOKEN_EXPIRY_MS - 1);

        csrf.csrfTokens.put("fresh", "token2");
        csrf.csrfTokenTimestamps.put("fresh", System.currentTimeMillis());

        csrf.cleanupExpired();

        assertThat(csrf.csrfTokens).doesNotContainKey("old");
        assertThat(csrf.csrfTokenTimestamps).doesNotContainKey("old");
        assertThat(csrf.csrfTokens).containsKey("fresh");
    }

    @Test
    void cleanupExpired_keepsNonExpiredEntries() {
        csrf.csrfTokens.put("active", "token");
        csrf.csrfTokenTimestamps.put("active", System.currentTimeMillis());

        csrf.cleanupExpired();

        assertThat(csrf.csrfTokens).containsKey("active");
    }

    @Test
    void cleanupExpired_calledByGenerateToken() {
        // Insert an already-expired entry
        csrf.csrfTokens.put("stale", "token");
        csrf.csrfTokenTimestamps.put("stale", System.currentTimeMillis() - CsrfProtection.CSRF_TOKEN_EXPIRY_MS - 1);

        // generateToken triggers cleanup
        csrf.generateToken("new-session");

        assertThat(csrf.csrfTokens).doesNotContainKey("stale");
    }

    // ===== secureEquals =====

    @Test
    void secureEquals_equalStrings() {
        assertThat(CsrfProtection.secureEquals("abc", "abc")).isTrue();
    }

    @Test
    void secureEquals_unequalStrings() {
        assertThat(CsrfProtection.secureEquals("abc", "xyz")).isFalse();
    }

    @Test
    void secureEquals_differentLengths() {
        assertThat(CsrfProtection.secureEquals("ab", "abc")).isFalse();
        assertThat(CsrfProtection.secureEquals("abc", "ab")).isFalse();
    }

    @Test
    void secureEquals_bothNull() {
        assertThat(CsrfProtection.secureEquals(null, null)).isTrue();
    }

    @Test
    void secureEquals_oneNull() {
        assertThat(CsrfProtection.secureEquals(null, "abc")).isFalse();
        assertThat(CsrfProtection.secureEquals("abc", null)).isFalse();
    }

    @Test
    void secureEquals_emptyStrings() {
        assertThat(CsrfProtection.secureEquals("", "")).isTrue();
    }

    @Test
    void secureEquals_emptyVsNonEmpty() {
        assertThat(CsrfProtection.secureEquals("", "a")).isFalse();
        assertThat(CsrfProtection.secureEquals("a", "")).isFalse();
    }
}
