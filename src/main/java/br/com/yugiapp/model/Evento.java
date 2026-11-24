package br.com.yugiapp.model;

import br.com.yugiapp.enums.TipoEventoEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Acontecimento na loja (torneio, noite de board games...), exibido no calendário do web e na tela de eventos do app.
 * <p>
 * As datas são o horário de parede do estabelecimento ({@code app.fuso-horario}), exatamente como a equipe informa no
 * calendário: "19:00" é gravado e exibido como 19:00, sem conversão de fuso.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_EVENTO")
public class Evento implements Serializable {

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NOME")
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO")
    private TipoEventoEnum tipo;

    @Column(name = "DESCRICAO")
    private String descricao;

    @Column(name = "DATA_HORA_INICIO")
    private LocalDateTime dataHoraInicio;

    @Column(name = "DATA_HORA_FIM")
    private LocalDateTime dataHoraFim;

    /** Valor da inscrição; nulo quando o evento é gratuito. */
    @Column(name = "VALOR_INSCRICAO")
    private Double valorInscricao;

    /** Limite de participantes; nulo quando não há limite. */
    @Column(name = "VAGAS")
    private Integer vagas;
}
