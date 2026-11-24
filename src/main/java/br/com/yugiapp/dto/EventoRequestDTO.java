package br.com.yugiapp.dto;

import br.com.yugiapp.enums.TipoEventoEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoRequestDTO {

    private String nome;
    private TipoEventoEnum tipo;
    private String descricao;
    /** Horário local do estabelecimento (sem fuso), como informado no calendário. */
    private LocalDateTime dataHoraInicio;
    private LocalDateTime dataHoraFim;
    /** Nulo ou zero: evento gratuito. */
    private Double valorInscricao;
    /** Nulo: sem limite de participantes. */
    private Integer vagas;
}
