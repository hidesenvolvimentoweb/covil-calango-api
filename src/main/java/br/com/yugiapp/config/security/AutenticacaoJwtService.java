package br.com.yugiapp.config.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converte um JWT válido na {@link Authentication} usada pela API: o principal é um {@link UsuarioAutenticado} e as
 * authorities são os perfis do token mais as permissões associadas a eles no banco. Usado tanto nas requisições
 * HTTP ({@link UserAuthenticationFilter}) quanto nas mensagens do WebSocket.
 */
@Service
@RequiredArgsConstructor
public class AutenticacaoJwtService {

    private final JwtTokenService jwtTokenService;
    private final PermissaoPerfilService permissaoPerfilService;

    public Optional<Authentication> autenticar(String token) {
        return jwtTokenService.validar(token).map(this::criarAutenticacao);
    }

    /** Indica se a autenticação possui alguma das autoridades informadas. */
    public static boolean possuiAlguma(Authentication autenticacao, String... autoridades) {
        if (autenticacao == null) {
            return false;
        }
        List<String> aceitas = List.of(autoridades);
        return autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(aceitas::contains);
    }

    /** Comanda do visitante do app, quando a autenticação é de um visitante. */
    public static Optional<String> comandaDoVisitante(Authentication autenticacao) {
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario
                && usuario.isVisitante()) {
            return Optional.of(usuario.numeroComanda());
        }
        return Optional.empty();
    }

    private Authentication criarAutenticacao(DecodedJWT jwt) {
        List<String> perfis = jwt.getClaim(JwtTokenService.CLAIM_PERFIS).asList(String.class);
        Set<SimpleGrantedAuthority> authorities = permissaoPerfilService.autoridades(perfis).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        UsuarioAutenticado usuario = new UsuarioAutenticado(jwt.getSubject(),
                jwt.getClaim(JwtTokenService.CLAIM_COMANDA).asString());
        return new UsernamePasswordAuthenticationToken(usuario, null, authorities);
    }
}
