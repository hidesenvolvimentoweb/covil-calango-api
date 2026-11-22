package br.com.yugiapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Situação de um pagamento da comanda. Somente {@link #PENDENTE} ainda pode mudar por ação do cliente;
 * {@link #PAGO} é definitivo (exceto por estorno feito no provedor).
 */
@Getter
@RequiredArgsConstructor
public enum StatusPagamentoEnum {

    PENDENTE("Pendente"),
    PAGO("Pago"),
    EXPIRADO("Expirado"),
    CANCELADO("Cancelado"),
    ESTORNADO("Estornado");

    private final String value;

}
