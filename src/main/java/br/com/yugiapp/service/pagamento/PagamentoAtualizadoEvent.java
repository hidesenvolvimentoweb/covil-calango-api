package br.com.yugiapp.service.pagamento;

import br.com.yugiapp.enums.StatusPagamentoEnum;

/**
 * Publicado quando o status de um pagamento muda. Os avisos (WebSocket, WhatsApp) só saem depois do commit,
 * para o cliente nunca ver "pago" de uma transação que acabou desfeita.
 *
 * @param comandaFechada a confirmação do pagamento fechou a comanda nesta transação
 */
public record PagamentoAtualizadoEvent(Long pagamentoId, String numeroComanda, StatusPagamentoEnum status,
                                       boolean comandaFechada) {
}
