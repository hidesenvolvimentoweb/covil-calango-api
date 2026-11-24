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
public class EventoResponseDTO {

    private Long id;
    private String nome;
    private TipoEventoEnum tipo;
    private String descricao;
    private LocalDateTime dataHoraInicio;
    private LocalDateTime dataHoraFim;
    private Double valorInscricao;
    private Integer vagas;
    /** Inscrições feitas pelo app. */
    private Long totalParticipantes;
    /** A comanda de quem consulta (app) está inscrita; nulo para a equipe. */
    private Boolean participando;
}
