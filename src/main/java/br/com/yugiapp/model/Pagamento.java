package br.com.yugiapp.model;

import br.com.yugiapp.enums.FormaPagamentoEnum;
import br.com.yugiapp.enums.MetodoPagamentoEnum;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tentativa de pagamento de uma comanda em um provedor (gateway). Uma comanda pode ter várias tentativas
 * (ex.: um PIX expirado e outro pago), mas no máximo uma {@link StatusPagamentoEnum#PENDENTE} por vez.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_PAGAMENTO")
public class Pagamento implements Serializable {

    /** Prefixo do external_reference enviado ao provedor; o restante é o id do pagamento. */
    public static final String PREFIXO_REFERENCIA = "PAG-";

    @Id
    @Column(name = "ID")
    @SequenceGenerator(name = "pagamentoIdGenerator", sequenceName = "tb_pagamento_seq", schema = "public", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pagamentoIdGenerator")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "FK_COMANDA")
    private Comanda comanda;

    @Column(name = "METODO", nullable = false)
    @Enumerated(EnumType.STRING)
    private MetodoPagamentoEnum metodo;

    @Column(name = "STATUS", nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusPagamentoEnum status;

    /** Valor cobrado, calculado no servidor no momento da geração da cobrança. */
    @Column(name = "VALOR", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    /** Provedor que processou o pagamento (ex.: MERCADO_PAGO). */
    @Column(name = "GATEWAY", nullable = false)
    private String gateway;

    /** Identificador do pagamento no provedor. */
    @Column(name = "GATEWAY_ID")
    private String gatewayId;

    /** Status original devolvido pelo provedor (ex.: pending, approved), para auditoria. */
    @Column(name = "STATUS_GATEWAY")
    private String statusGateway;

    /** Chave de idempotência da criação no provedor: repetir a chamada não gera uma segunda cobrança. */
    @Column(name = "IDEMPOTENCY_KEY", nullable = false)
    private String idempotencyKey;

    /** Código "copia e cola" do PIX. */
    @ToString.Exclude
    @Column(name = "PIX_COPIA_E_COLA", columnDefinition = "TEXT")
    private String pixCopiaECola;

    /** Imagem PNG do QR Code do PIX, em Base64. */
    @ToString.Exclude
    @Column(name = "PIX_QR_CODE_BASE64", columnDefinition = "TEXT")
    private String pixQrCodeBase64;

    /** Bandeira do cartão (payment_method_id do provedor, ex.: visa, master). */
    @Column(name = "CARTAO_BANDEIRA", length = 40)
    private String cartaoBandeira;

    /** Crédito ou débito, conforme o cartão usado (payment_type_id do provedor). */
    @Enumerated(EnumType.STRING)
    @Column(name = "CARTAO_TIPO", length = 20)
    private FormaPagamentoEnum cartaoTipo;

    /** Últimos 4 dígitos do cartão; o número completo nunca passa pela API. */
    @Column(name = "CARTAO_FINAL", length = 4)
    private String cartaoFinal;

    /** Motivo da recusa devolvido pelo provedor (status_detail, ex.: cc_rejected_insufficient_amount). */
    @Column(name = "MOTIVO_RECUSA", length = 80)
    private String motivoRecusa;

    /** Preferência do Checkout Pro (pagamento pela conta do Mercado Pago). */
    @Column(name = "PREFERENCIA_ID", length = 80)
    private String preferenciaId;

    /** Endereço do Checkout Pro que o app abre para o cliente pagar na conta do Mercado Pago. */
    @ToString.Exclude
    @Column(name = "URL_CHECKOUT", columnDefinition = "TEXT")
    private String urlCheckout;

    /** Verificação do banco (3DS) pendente: endereço do desafio. Só vai na resposta da criação; não é gravado. */
    @Transient
    @ToString.Exclude
    private String desafioUrl;

    /** Verificação do banco (3DS): dados do desafio (creq), enviados ao endereço do desafio. Não é gravado. */
    @Transient
    @ToString.Exclude
    private String desafioCreq;

    @Column(name = "DT_HORA_CRIACAO", nullable = false)
    private LocalDateTime dataHoraCriacao;

    @Column(name = "DT_HORA_EXPIRACAO")
    private LocalDateTime dataHoraExpiracao;

    @Column(name = "DT_HORA_PAGAMENTO")
    private LocalDateTime dataHoraPagamento;

    @Column(name = "DT_HORA_ATUALIZACAO")
    private LocalDateTime dataHoraAtualizacao;

    /** Referência enviada ao provedor (external_reference), usada para conferir que a notificação é deste pagamento. */
    public String getReferencia() {
        return PREFIXO_REFERENCIA + id;
    }
}
