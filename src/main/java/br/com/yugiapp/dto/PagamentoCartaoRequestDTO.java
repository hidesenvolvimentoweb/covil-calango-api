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
    private String email;
    /** CPF ou CNPJ. */
    private String tipoDocumento;
    private String documento;
}
