package br.com.yugiapp.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimiteTentativasServiceTest {

    private static final Duration JANELA = Duration.ofMinutes(15);

    /** Relógio que o teste avança manualmente. */
    private static final class RelogioAjustavel extends Clock {
        private Instant agora = Instant.parse("2026-11-23T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioAjustavel relogio = new RelogioAjustavel();
    private final LimiteTentativasService service = new LimiteTentativasService(relogio);

    @Test
    void recusaComTooManyRequestsDepoisDoLimite() {
        for (int i = 0; i < 3; i++) {
            service.consumir("ip:1", 3, JANELA);
        }
        assertThatThrownBy(() -> service.consumir("ip:1", 3, JANELA))
                .isInstanceOfSatisfying(ResponseStatusException.class, erro -> {
                    assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(erro.getReason()).isEqualTo("Muitas tentativas. Tente novamente em 15 minutos");
                });
        // Cada chave tem o próprio contador.
        service.consumir("ip:2", 3, JANELA);
    }

    @Test
    void liberaQuandoAJanelaTermina() {
        for (int i = 0; i < 3; i++) {
            service.consumir("ip:1", 3, JANELA);
        }
        relogio.avancar(JANELA);
        service.consumir("ip:1", 3, JANELA);
        assertThat(service.tentar("ip:1", 3, JANELA)).isTrue();
    }

    @Test
    void falhasBloqueiamSemContarAsTentativasLiberadas() {
        for (int i = 0; i < 5; i++) {
            service.exigirDisponivel("login:a", 5);
            service.registrarFalha("login:a", JANELA);
        }
        relogio.avancar(Duration.ofMinutes(14));
        assertThatThrownBy(() -> service.exigirDisponivel("login:a", 5))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getReason()).endsWith("em 1 minuto"));
    }

    @Test
    void limparZeraAChave() {
        for (int i = 0; i < 5; i++) {
            service.registrarFalha("login:a", JANELA);
        }
        service.limpar("login:a");
        service.exigirDisponivel("login:a", 5);
    }

    @Test
    void limpezaRemoveAsJanelasVencidas() {
        service.registrarFalha("login:a", JANELA);
        relogio.avancar(JANELA);
        service.removerExpiradas();
        service.exigirDisponivel("login:a", 1);
    }
}
