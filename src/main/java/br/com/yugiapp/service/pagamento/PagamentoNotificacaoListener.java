package br.com.yugiapp.service.pagamento;

import br.com.yugiapp.controller.ComandaController;
import br.com.yugiapp.dto.PagamentoResponseDTO;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.repository.PagamentoRepository;
import br.com.yugiapp.service.ComandaService;
import br.com.yugiapp.service.WhatsappService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa o app e a equipe quando um pagamento muda de status:
 * <ul>
 *     <li>{@code /topic/pagamentos/{comanda}}: o pagamento atualizado (a tela do QR Code troca para "pago");</li>
 *     <li>{@code /topic/pagamentos}: o resumo do pagamento, para a equipe acompanhar as comandas;</li>
 *     <li>{@code /topic/comandas/{comanda}}: a comanda fechada pelo pagamento (o app encerra a sessão);</li>
 *     <li>WhatsApp: confirmação do pagamento, se o cliente consentiu.</li>
 * </ul>
 * Falhas no aviso não desfazem o pagamento; o app também pode consultar {@code GET /api/pagamento/comanda/{numero}}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagamentoNotificacaoListener {

    public static final String TOPICO_PAGAMENTOS = "/topic/pagamentos/";
    /** Todos os pagamentos, para a equipe (tela de comandas do web); sem QR Code. */
    public static final String TOPICO_PAGAMENTOS_EQUIPE = "/topic/pagamentos";

    private final PagamentoRepository pagamentoRepository;
    private final ComandaService comandaService;
    private final WhatsappService whatsappService;
    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAtualizarPagamento(PagamentoAtualizadoEvent evento) {
        String numero = evento.numeroComanda();
        try {
            pagamentoRepository.findById(evento.pagamentoId()).ifPresent(pagamento -> {
                messagingTemplate.convertAndSend(TOPICO_PAGAMENTOS + numero, PagamentoResponseDTO.of(pagamento));
                messagingTemplate.convertAndSend(TOPICO_PAGAMENTOS_EQUIPE, PagamentoResponseDTO.resumo(pagamento));
                if (evento.status() == StatusPagamentoEnum.PAGO) {
                    whatsappService.notificarPagamentoAprovado(pagamento.getComanda(), pagamento.getValor());
                }
            });
            if (evento.comandaFechada()) {
                messagingTemplate.convertAndSend(ComandaController.TOPICO_COMANDAS + numero,
                        comandaService.getResponseDTOByNumero(numero));
            }
        } catch (RuntimeException exception) {
            log.error("Falha ao avisar a atualização do pagamento {} da comanda {}", evento.pagamentoId(), numero, exception);
        }
    }
}
