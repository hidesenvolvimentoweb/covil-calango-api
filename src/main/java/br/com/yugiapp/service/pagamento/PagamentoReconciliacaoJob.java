package br.com.yugiapp.service.pagamento;

import br.com.yugiapp.service.PagamentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Confere periodicamente no provedor os pagamentos pendentes: cobre webhooks perdidos (API fora do ar, ambiente
 * local sem URL pública) e encerra os PIX expirados.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagamentoReconciliacaoJob {

    private final PagamentoService pagamentoService;

    @Scheduled(fixedDelayString = "${app.pagamento.reconciliacao-intervalo-ms:60000}",
            initialDelayString = "${app.pagamento.reconciliacao-intervalo-ms:60000}")
    public void reconciliar() {
        if (!pagamentoService.isGatewayConfigurado()) {
            return;
        }
        for (Long pagamentoId : pagamentoService.pendentesParaReconciliar()) {
            try {
                pagamentoService.reconciliar(pagamentoId);
            } catch (RuntimeException exception) {
                log.warn("Não foi possível reconciliar o pagamento {}: {}", pagamentoId, exception.getMessage());
            }
        }
    }
}
