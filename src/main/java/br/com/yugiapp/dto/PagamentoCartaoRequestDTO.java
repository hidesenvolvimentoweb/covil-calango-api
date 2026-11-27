package br.com.yugiapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dados enviados pelo formulário de cartão do Mercado Pago no app. O cartão vem como token: o número, a validade e o
 * código de segurança nunca chegam à API. O valor não é informado: é sempre o total da comanda, calculado no servidor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoCartaoRequestDTO {

    /** Token do cartão (uso único), gerado pelo formulário do Mercado Pago. */
    private String token;
    /** Bandeira (payment_method_id), ex.: visa, master. */
    private String bandeira;
    /** Emissor (issuer_id), quando informado pelo formulário. */
    private String emissorId;
    /** E-mail do titular; opcional com cartão salvo (o formulário não pede de novo). */
    private String email;
    /** CPF ou CNPJ; opcional com cartão salvo. */
    private String tipoDocumento;
    private String documento;
    /**
     * Salvar o cartão neste celular (o celular vai no cabeçalho X-Chave-Aparelho). O cartão é salvo depois da
     * cobrança, se ela não for recusada.
     */
    private Boolean salvarCartao;
}
