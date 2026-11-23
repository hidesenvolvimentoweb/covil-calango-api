package br.com.yugiapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Como a comanda foi paga. PIX pelo app é registrado automaticamente quando o pagamento é aprovado; no fechamento
 * pelo balcão, a equipe informa a forma recebida (o PIX também pode ser recebido no balcão).
 */
@Getter
@RequiredArgsConstructor
public enum FormaPagamentoEnum {

    PIX("Pix"),
    DEBITO("Débito"),
    CREDITO("Crédito"),
    DINHEIRO("Dinheiro");

    private final String value;
}
