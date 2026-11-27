package br.com.yugiapp.service.pagamento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Preferência do Checkout Pro: o cliente paga na própria conta do Mercado Pago (com um cartão salvo nela).
 *
 * @param referencia      identificador do pagamento na API (external_reference)
 * @param idempotencyKey  chave que impede preferências duplicadas em caso de nova tentativa
 * @param nomePagador     nome do cliente na comanda; vai como nome e sobrenome do pagador (melhora a aprovação)
 * @param emailPagador    e-mail do cliente, quando ele tem cadastro; nulo para o visitante (nunca o e-mail da loja,
 *                        que o Mercado Pago sugeriria no login da conta)
 * @param celularPagador  celular do cliente, só dígitos (com ou sem o 55), quando informado
 */
public record SolicitacaoPreferencia(
        String referencia,
        BigDecimal valor,
        String descricao,
        OffsetDateTime expiraEm,
        String idempotencyKey,
        String nomePagador,
        String emailPagador,
        String celularPagador) {

    public SolicitacaoPreferencia(String referencia, BigDecimal valor, String descricao, OffsetDateTime expiraEm,
                                  String idempotencyKey) {
        this(referencia, valor, descricao, expiraEm, idempotencyKey, null, null, null);
    }
}
