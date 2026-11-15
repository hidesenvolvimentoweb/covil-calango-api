package br.com.yugiapp.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Autentica a requisição a partir do JWT, lido do cookie {@value AuthCookieService#NOME_COOKIE} (web) ou do
 * cabeçalho {@code Authorization: Bearer} (app). Token ausente ou inválido não bloqueia a requisição aqui:
 * ela segue anônima e as regras do {@link JwtSecurityConfig} decidem se a rota exige autenticação.
 * <p>
 * As authorities da requisição são os perfis do token e as permissões associadas a eles no banco
 * ({@link AutenticacaoJwtService}); o principal é um {@link UsuarioAutenticado}.
 * <p>
 * Proteção contra CSRF: o navegador envia o cookie sozinho, então requisições que alteram dados autenticadas
 * pelo cookie precisam do cabeçalho {@value #CABECALHO_CSRF}. Um site de terceiros só consegue enviar esse
 * cabeçalho passando pelo preflight de CORS, que é negado para origens não confiáveis.
 */
@RequiredArgsConstructor
public class UserAuthenticationFilter extends OncePerRequestFilter {

    public static final String CABECALHO_CSRF = "X-Requested-With";
    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS");

    private final AutenticacaoJwtService autenticacaoJwtService;
    private final AuthCookieService authCookieService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String tokenCabecalho = lerBearer(request.getHeader("Authorization"));
        boolean viaCookie = tokenCabecalho == null;
        String token = viaCookie ? authCookieService.ler(request) : tokenCabecalho;

        if (!viaCookie || METODOS_SEGUROS.contains(request.getMethod()) || request.getHeader(CABECALHO_CSRF) != null) {
            autenticacaoJwtService.autenticar(token)
                    .ifPresent(autenticacao -> SecurityContextHolder.getContext().setAuthentication(autenticacao));
        }

        filterChain.doFilter(request, response);
    }

    /** Extrai o token de um valor {@code Bearer <token>}; {@code null} quando ausente ou em outro formato. */
    public static String lerBearer(String authorization) {
        String prefixo = "Bearer ";
        if (authorization != null && authorization.startsWith(prefixo)) {
            String token = authorization.substring(prefixo.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
