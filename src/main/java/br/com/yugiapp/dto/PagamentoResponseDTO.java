package br.com.yugiapp.dto;

import br.com.yugiapp.enums.FormaPagamentoEnum;
import br.com.yugiapp.enums.MetodoPagamentoEnum;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.model.Pagamento;
import br.com.yugiapp.service.pagamento.MotivoRecusaCartao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class PagamentoResponseDTO implements Serializable {

    private Long id;

    private String comanda;

    private MetodoPagamentoEnum metodo;

    private StatusPagamentoEnum status;

    private BigDecimal valor;

    /** Código "copia e cola"; enviado somente enquanto o pagamento está pendente. */
    private String pixCopiaECola;

    /** QR Code (PNG em Base64, sem o prefixo data:image/png;base64,); somente enquanto pendente. */
    private String pixQrCodeBase64;

    /** Link do Checkout Pro (pagar na conta do Mercado Pago); somente enquanto pendente. */
    private String urlCheckout;

    /** Verificação do banco (3DS) pendente: endereço e dados do desafio. Só na resposta da cobrança do cartão. */
    private String desafioUrl;

    private String desafioCreq;

    /** Bandeira (ex.: visa) e últimos 4 dígitos do cartão. */
    private String cartaoBandeira;

    private String cartaoFinal;

    /** CREDITO ou DEBITO, conforme o cartão usado. */
    private FormaPagamentoEnum cartaoTipo;

    /** Motivo da recusa do cartão, em texto para o cliente; somente quando recusado. */
    private String mensagemRecusa;

    /** Resultado do pedido para salvar o cartão no celular; nulo quando não foi pedido. Só na resposta da cobrança. */
    private Boolean cartaoSalvo;

    private LocalDateTime dataHoraCriacao;

    private LocalDateTime dataHoraExpiracao;

    private LocalDateTime dataHoraPagamento;

    public static PagamentoResponseDTO of(Pagamento pagamento) {
        PagamentoResponseDTO dto = resumo(pagamento);
        if (pagamento.getStatus() == StatusPagamentoEnum.PENDENTE) {
            dto.setPixCopiaECola(pagamento.getPixCopiaECola());
            dto.setPixQrCodeBase64(pagamento.getPixQrCodeBase64());
            dto.setUrlCheckout(pagamento.getUrlCheckout());
            dto.setDesafioUrl(pagamento.getDesafioUrl());
            dto.setDesafioCreq(pagamento.getDesafioCreq());
        }
        return dto;
    }

    /** Sem o QR Code e o "copia e cola": para listagens e para a equipe, que não precisam pagar. */
    public static PagamentoResponseDTO resumo(Pagamento pagamento) {
        return PagamentoResponseDTO.builder()
                .id(pagamento.getId())
                .comanda(pagamento.getComanda().getNumero())
                .metodo(pagamento.getMetodo())
                .status(pagamento.getStatus())
                .valor(pagamento.getValor())
                .cartaoBandeira(pagamento.getCartaoBandeira())
                .cartaoFinal(pagamento.getCartaoFinal())
                .cartaoTipo(pagamento.getCartaoTipo())
                .mensagemRecusa(pagamento.getStatus() == StatusPagamentoEnum.RECUSADO
                        ? MotivoRecusaCartao.mensagem(pagamento.getMotivoRecusa()) : null)
                .dataHoraCriacao(pagamento.getDataHoraCriacao())
                .dataHoraExpiracao(pagamento.getDataHoraExpiracao())
                .dataHoraPagamento(pagamento.getDataHoraPagamento())
                .build();
    }
}
