package br.com.yugiapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MetodoPagamentoEnum {

    PIX("PIX"),
    /**
     * Cartão de crédito ou débito digitado no app (formulário do Mercado Pago, que transforma o cartão em token). O nome
     * ficou do tempo em que só o crédito era aceito; o tipo do cartão fica em Pagamento.cartaoTipo.
     */
    CARTAO_CREDITO("Cartão"),
    /** Pagamento na conta do cliente no Mercado Pago (cartão salvo lá), pelo Checkout Pro. */
    CARTEIRA_MERCADO_PAGO("Mercado Pago");

    private final String value;

}
