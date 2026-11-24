package br.com.yugiapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Inscrição de uma comanda em um evento, feita pelo app. Nos eventos pagos, o valor da inscrição entra na comanda
 * como um pedido sem produto ({@link #pedido}); sair do evento remove esse pedido.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_EVENTO_PARTICIPANTE")
public class EventoParticipante implements Serializable {

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "FK_EVENTO")
    private Evento evento;

    @ManyToOne(optional = false)
    @JoinColumn(name = "FK_COMANDA")
    private Comanda comanda;

    /** Pedido com o valor da inscrição; nulo nos eventos gratuitos. */
    @OneToOne
    @JoinColumn(name = "FK_PEDIDO")
    private Pedido pedido;

    /** Horário do servidor (mesma referência de Pedido.dataHora). */
    @Column(name = "DT_HORA_INSCRICAO")
    private LocalDateTime dataHoraInscricao;
}
