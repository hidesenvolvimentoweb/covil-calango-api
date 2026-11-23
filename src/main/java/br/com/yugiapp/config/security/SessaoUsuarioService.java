package br.com.yugiapp.config.security;

import br.com.yugiapp.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Confere se a sessão (JWT) de um usuário do web ainda vale. O token é recusado quando o usuário não existe mais
 * (excluído ou com o e-mail alterado) ou quando foi emitido antes da última troca de senha ou de perfis
 * ({@code Usuario.acessoAlteradoEm}). As sessões dos visitantes do app não passam por aqui: elas são da comanda.
 */
@Service
@RequiredArgsConstructor
public class SessaoUsuarioService {

    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public boolean sessaoValida(String email, Instant emitidoEm) {
        if (email == null || emitidoEm == null) {
            return false;
        }
        return usuarioRepository.findSessaoByEmail(email)
                .map(sessao -> sessao.getAcessoAlteradoEm() == null
                        // Datas gravadas no fuso da JVM, como as demais da aplicação.
                        || !LocalDateTime.ofInstant(emitidoEm, ZoneId.systemDefault()).isBefore(sessao.getAcessoAlteradoEm()))
                .orElse(false);
    }
}
