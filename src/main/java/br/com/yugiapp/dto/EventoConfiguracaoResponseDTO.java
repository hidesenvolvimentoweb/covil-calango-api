package br.com.yugiapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Funcionamento da loja usado no cadastro e no calendário de eventos (vem da tela de parâmetros). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoConfiguracaoResponseDTO {

    /** Horário sugerido para o início de um evento ("14:00"). */
    private String horaAbertura;
    /** Dias sem expediente, na numeração ISO (1 = segunda ... 7 = domingo). */
    private List<Integer> diasFechados;
    private Integer duracaoPadraoMinutos;
    /** Intervalo das opções de horário no cadastro. */
    private Integer intervaloHorariosMinutos;
}
