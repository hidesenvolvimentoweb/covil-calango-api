package br.com.yugiapp.config.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Regras de acesso que dependem do dado da requisição (e não só da rota): o visitante do app só enxerga e
 * altera a própria comanda; a equipe acessa qualquer comanda conforme as permissões do seu perfil.
 */
@Service
public class AcessoService {

    /** Comanda do visitante autenticado, quando a requisição vem do app. */
    public Optional<String> comandaDoVisitante() {
        return AutenticacaoJwtService.comandaDoVisitante(SecurityContextHolder.getContext().getAuthentication());
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
        return AutenticacaoJwtService.possuiAlguma(SecurityContextHolder.getContext().getAuthentication(), autoridades);
    }
}
