package br.com.yugiapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Participante de um evento, exibido no detalhe do evento (web e app). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoParticipanteResponseDTO {

    private Long id;
    /** Nome informado na comanda. */
    private String nome;
    /** Número da comanda: só para a equipe (nulo no app). */
    private String comanda;
    private LocalDateTime dataHoraInscricao;
    /** A inscrição é da comanda de quem consulta (app). */
    private boolean voce;
}
