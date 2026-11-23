package br.com.yugiapp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Avisa a equipe (tela de comandas do web) que uma comanda mudou: abertura, fechamento ou pedidos que alteram o
 * valor. A mensagem leva só o número; a tela recarrega a lista pela consulta REST, que exige permissão.
 * <p>
 * Chamado pelos controllers depois que o serviço retorna, ou seja, com a transação já confirmada: a tela nunca
 * recarrega antes de a alteração estar gravada. Pagamentos seguem pelo tópico /topic/pagamentos.
 */
@Service
@RequiredArgsConstructor
public class ComandaNotificacaoService {

    /** Tópico da equipe; o do app é /topic/comandas/{numero} (ComandaController.TOPICO_COMANDAS). */
    public static final String TOPICO_COMANDAS_EQUIPE = "/topic/comandas";

    private final SimpMessagingTemplate messagingTemplate;

    public void notificarAlteracao(String numeroComanda) {
        if (numeroComanda != null) {
            messagingTemplate.convertAndSend(TOPICO_COMANDAS_EQUIPE, Map.of("numero", numeroComanda));
        }
    }
}
