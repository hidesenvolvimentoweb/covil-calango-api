package br.com.yugiapp.model;

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
