package br.com.yugiapp.config.security;

import br.com.yugiapp.repository.UsuarioRepository;
import br.com.yugiapp.repository.UsuarioRepository.SessaoUsuario;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessaoUsuarioServiceTest {

    private static final String EMAIL = "ana@covil";
    private static final LocalDateTime TROCA_DE_SENHA = LocalDateTime.of(2026, 11, 23, 10, 0, 0);

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final SessaoUsuarioService service = new SessaoUsuarioService(usuarioRepository);

    private static Instant instante(LocalDateTime dataHora) {
        return dataHora.atZone(ZoneId.systemDefault()).toInstant();
    }

    private void usuarioComAcessoAlteradoEm(LocalDateTime alteradoEm) {
        SessaoUsuario sessao = mock(SessaoUsuario.class);
        when(sessao.getAcessoAlteradoEm()).thenReturn(alteradoEm);
        when(usuarioRepository.findSessaoByEmail(EMAIL)).thenReturn(Optional.of(sessao));
    }

    @Test
    void tokenEmitidoAntesDaTrocaDeSenhaEhRecusado() {
        usuarioComAcessoAlteradoEm(TROCA_DE_SENHA);

        assertThat(service.sessaoValida(EMAIL, instante(TROCA_DE_SENHA.minusMinutes(5)))).isFalse();
        // O login feito no mesmo segundo da troca (o JWT guarda segundos) ou depois continua valendo.
        assertThat(service.sessaoValida(EMAIL, instante(TROCA_DE_SENHA))).isTrue();
        assertThat(service.sessaoValida(EMAIL, instante(TROCA_DE_SENHA.plusMinutes(1)))).isTrue();
    }

    @Test
    void semAlteracaoRegistradaASessaoVale() {
        usuarioComAcessoAlteradoEm(null);

        assertThat(service.sessaoValida(EMAIL, instante(TROCA_DE_SENHA))).isTrue();
    }

    @Test
    void usuarioExcluidoOuComOutroEmailNaoTemSessao() {
        when(usuarioRepository.findSessaoByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(service.sessaoValida(EMAIL, instante(TROCA_DE_SENHA))).isFalse();
        assertThat(service.sessaoValida(null, instante(TROCA_DE_SENHA))).isFalse();
        assertThat(service.sessaoValida(EMAIL, null)).isFalse();
    }
}
