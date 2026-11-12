package br.com.yugiapp.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;

/**
 * Cookie HttpOnly que carrega o JWT da sessão do web. Por ser HttpOnly, o JavaScript da página não tem
 * acesso ao token. Configuração:
 * <ul>
 *     <li>{@code AUTH_COOKIE_SECURE}: {@code true} em produção (HTTPS).</li>
 *     <li>{@code AUTH_COOKIE_SAME_SITE}: {@code Lax} quando web e API estão no mesmo site (mesmo domínio
 *     registrável); {@code None} quando estão em domínios diferentes, o que exige {@code Secure=true}.</li>
 * </ul>
 */
@Service
public class AuthCookieService {

    public static final String NOME_COOKIE = "covil_token";

    private final boolean secure;
    private final String sameSite;

    public AuthCookieService(@Value("${app.auth.cookie.secure:false}") boolean secure,
                             @Value("${app.auth.cookie.same-site:Lax}") String sameSite) {
        if ("None".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalStateException("AUTH_COOKIE_SAME_SITE=None exige AUTH_COOKIE_SECURE=true");
        }
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie criar(String token, Duration validade) {
        return base(token).maxAge(validade).build();
    }

    public ResponseCookie limpar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public String ler(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NOME_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOME_COOKIE, valor)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api");
    }
}
