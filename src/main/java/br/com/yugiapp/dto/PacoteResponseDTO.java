package br.com.yugiapp.dto;

import br.com.yugiapp.enums.CategoriaPacoteEnum;
import br.com.yugiapp.enums.StatusPacoteEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacoteResponseDTO {

    private Long id;
    private String codigo;
    private CategoriaPacoteEnum categoria;
    private String descricao;
    private String destinatario;
    private StatusPacoteEnum status;
    /** Nome de quem deixou (o informado na comanda). */
    private String remetente;
    /** Comanda de quem deixou: só para a equipe (nula no app). */
    private String comanda;
    /** Taxa cobrada na comanda; nula quando não houve cobrança. */
    private Double valorTaxa;
    private String fotoDeposito;
    private String fotoRetirada;
    private LocalDateTime dataHoraDeposito;
    private LocalDateTime dataHoraRetirada;
    /** Atendente que entregou: só para a equipe. */
    private String retiradoPor;
    private LocalDateTime dataHoraCancelamento;
}
