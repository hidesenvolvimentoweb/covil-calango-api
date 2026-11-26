package br.com.yugiapp.model;

import br.com.yugiapp.enums.CategoriaPacoteEnum;
import br.com.yugiapp.enums.StatusPacoteEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Pacote que um cliente deixa na loja (pelo app) para outra pessoa retirar. Quem deixa tira uma foto do item e
 * informa quem vai receber; a API gera o {@link #codigo}, que quem recebe informa ao atendente. A taxa do pacote
 * entra na comanda de quem deixou como um pedido sem produto ({@link #pedido}). Na retirada, o atendente registra a
 * foto do item entregue e dá baixa pelo web.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_PACOTE")
public class Pacote implements Serializable {

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código de retirada (único, sem caracteres ambíguos como 0/O e 1/I). */
    @Column(name = "CODIGO")
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORIA")
    private CategoriaPacoteEnum categoria;

    /** O que foi deixado (ex.: "Playmat do Dark Magician"). */
    @Column(name = "DESCRICAO")
    private String descricao;

    /** Nome de quem vai receber. */
    @Column(name = "DESTINATARIO")
    private String destinatario;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private StatusPacoteEnum status;

    /** Comanda de quem deixou o pacote (a taxa é cobrada nela). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "FK_COMANDA")
    private Comanda comanda;

    /** Pedido com a taxa do pacote; nulo se a taxa for zero ou se o pedido foi excluído da comanda. */
    @OneToOne
    @JoinColumn(name = "FK_PEDIDO")
    private Pedido pedido;

    /** Caminho público da foto tirada por quem deixou. */
    @Column(name = "FOTO_DEPOSITO")
    private String fotoDeposito;

    /** Caminho público da foto tirada pelo atendente na entrega. */
    @Column(name = "FOTO_RETIRADA")
    private String fotoRetirada;

    @Column(name = "DT_HORA_DEPOSITO")
    private LocalDateTime dataHoraDeposito;

    @Column(name = "DT_HORA_RETIRADA")
    private LocalDateTime dataHoraRetirada;

    /** Atendente que entregou o pacote (login do web). */
    @Column(name = "RETIRADO_POR")
    private String retiradoPor;

    @Column(name = "DT_HORA_CANCELAMENTO")
    private LocalDateTime dataHoraCancelamento;

    /** Pedido da taxa tirado da comanda no cancelamento (não gravado): avisa o app que o valor saiu. */
    @Transient
    @JsonIgnore
    private Pedido taxaRemovida;
}
