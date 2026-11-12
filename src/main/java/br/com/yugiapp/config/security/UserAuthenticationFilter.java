package br.com.yugiapp.config.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Autentica a requisição a partir do JWT, lido do cookie {@value AuthCookieService#NOME_COOKIE} (web) ou do
 * cabeçalho {@code Authorization: Bearer} (app). Token ausente ou inválido não bloqueia a requisição aqui:
 * ela segue anônima e as regras do {@link JwtSecurityConfig} decidem se a rota exige autenticação.
 * <p>
 * Proteção contra CSRF: o navegador envia o cookie sozinho, então requisições que alteram dados autenticadas
 * pelo cookie precisam do cabeçalho {@value #CABECALHO_CSRF}. Um site de terceiros só consegue enviar esse
 * cabeçalho passando pelo preflight de CORS, que é negado para origens não confiáveis.
 */
@RequiredArgsConstructor
public class UserAuthenticationFilter extends OncePerRequestFilter {

    public static final String CABECALHO_CSRF = "X-Requested-With";
    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String PREFIXO_BEARER = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final AuthCookieService authCookieService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String tokenCabecalho = lerCabecalho(request);
        String token = tokenCabecalho != null ? tokenCabecalho : authCookieService.ler(request);
        boolean viaCookie = tokenCabecalho == null;

        jwtTokenService.validar(token)
                .filter(jwt -> !viaCookie || METODOS_SEGUROS.contains(request.getMethod())
                        || request.getHeader(CABECALHO_CSRF) != null)
                .ifPresent(this::autenticar);

        filterChain.doFilter(request, response);
    }

    private void autenticar(DecodedJWT jwt) {
        List<String> perfis = jwt.getClaim(JwtTokenService.CLAIM_PERFIS).asList(String.class);
        List<SimpleGrantedAuthority> authorities = perfis == null ? List.of()
                : perfis.stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, authorities));
    }

    private String lerCabecalho(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(PREFIXO_BEARER)) {
            String token = authorization.substring(PREFIXO_BEARER.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
