package br.com.yugiapp.config.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita tentativas por chave (ex.: IP ou e-mail) dentro de uma janela de tempo, contra força bruta de senhas e
 * envio em massa de e-mails e solicitações. Os contadores ficam na memória da API: reiniciar a aplicação os zera,
 * o que é aceitável para este fim (a API roda em uma única instância).
 */
@Service
public class LimiteTentativasService {

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public LimiteTentativasService() {
        this(Clock.systemUTC());
    }

    LimiteTentativasService(Clock clock) {
        this.clock = clock;
    }

    /** Conta uma tentativa e recusa com 429 quando a chave passou do limite na janela. */
    public void consumir(String chave, int maximo, Duration duracao) {
        if (!tentar(chave, maximo, duracao)) {
            throw muitasTentativas(chave);
        }
    }

    /** Conta uma tentativa; {@code false} quando a chave passou do limite na janela. */
    public boolean tentar(String chave, int maximo, Duration duracao) {
        return incrementar(chave, duracao).contagem() <= maximo;
    }

    /** Recusa com 429, sem contar uma nova tentativa, quando a chave já atingiu o limite (ex.: senhas erradas). */
    public void exigirDisponivel(String chave, int maximo) {
        Janela janela = janelaAtual(chave);
        if (janela != null && janela.contagem() >= maximo) {
            throw muitasTentativas(chave);
        }
    }

    /** Conta uma falha (ex.: senha errada), sem recusar a requisição atual. */
    public void registrarFalha(String chave, Duration duracao) {
        incrementar(chave, duracao);
    }

    /** Zera a chave (ex.: login bem-sucedido). */
    public void limpar(String chave) {
        janelas.remove(chave);
    }

    @Scheduled(fixedDelayString = "PT10M")
    void removerExpiradas() {
        Instant agora = clock.instant();
        janelas.values().removeIf(janela -> janela.expirada(agora));
    }

    private Janela incrementar(String chave, Duration duracao) {
        Instant agora = clock.instant();
        return janelas.compute(chave, (k, atual) -> atual == null || atual.expirada(agora)
                ? new Janela(agora.plus(duracao), 1)
                : new Janela(atual.fim(), atual.contagem() + 1));
    }

    private Janela janelaAtual(String chave) {
        Janela janela = janelas.get(chave);
        return janela == null || janela.expirada(clock.instant()) ? null : janela;
    }

    private ResponseStatusException muitasTentativas(String chave) {
        Janela janela = janelaAtual(chave);
        long minutos = janela == null ? 1
                : Math.max(1, (Duration.between(clock.instant(), janela.fim()).toSeconds() + 59) / 60);
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas. Tente novamente em " + minutos + (minutos == 1 ? " minuto" : " minutos"));
    }

    private record Janela(Instant fim, int contagem) {
        boolean expirada(Instant agora) {
            return !agora.isBefore(fim);
        }
    }
}
