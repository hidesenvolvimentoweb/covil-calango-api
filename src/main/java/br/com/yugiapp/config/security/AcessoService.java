package br.com.yugiapp.config.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Optional;

/**
 * Regras de acesso que dependem do dado da requisição (e não só da rota): o visitante do app só enxerga e
 * altera a própria comanda; a equipe acessa qualquer comanda conforme as permissões do seu perfil.
 */
@Service
public class AcessoService {

    /** Comanda do visitante autenticado, quando a requisição vem do app. */
    public Optional<String> comandaDoVisitante() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario
                && usuario.isVisitante()) {
            return Optional.of(usuario.numeroComanda());
        }
        return Optional.empty();
    }

    /**
     * Garante acesso à comanda: liberado para quem tem uma das permissões da equipe ou para o visitante
     * dono da comanda.
     */
    public void validarComanda(String numeroComanda, String... permissoesEquipe) {
        if (possuiAlguma(Permissoes.exigir(permissoesEquipe))) {
            return;
        }
        boolean donoDaComanda = numeroComanda != null
                && comandaDoVisitante().map(numeroComanda::equals).orElse(false);
        if (!donoDaComanda) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem permissão para acessar esta comanda");
        }
    }

    public boolean possuiAlguma(String... autoridades) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null) {
            return false;
        }
        return autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(autoridade -> Arrays.asList(autoridades).contains(autoridade));
    }
}
