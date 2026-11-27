package br.com.yugiapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Celular de um cliente que salvou cartões (o app não tem login). O app guarda uma chave aleatória do aparelho e a
 * envia ao pagar; a API guarda só o hash dela ({@link #chaveHash}), ligado ao cliente criado no provedor de
 * pagamentos ({@link #clienteGatewayId}), onde os cartões ficam salvos. Nenhum dado do cartão é gravado aqui.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_CARTAO_APARELHO")
public class CartaoAparelho implements Serializable {

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 (hexadecimal) da chave do aparelho: com o banco, ninguém consegue se passar pelo celular. */
    @Column(name = "CHAVE_HASH")
    private String chaveHash;

    /** Provedor onde o cliente foi criado (ex.: MERCADO_PAGO). */
    @Column(name = "GATEWAY")
    private String gateway;

    /** Cliente no provedor (ex.: customer do Mercado Pago); nulo até o primeiro cartão ser salvo. */
    @Column(name = "CLIENTE_GATEWAY_ID")
    private String clienteGatewayId;

    @Column(name = "DT_HORA_CRIACAO")
    private LocalDateTime dataHoraCriacao;

    @Column(name = "DT_HORA_ULTIMO_USO")
    private LocalDateTime dataHoraUltimoUso;
}
