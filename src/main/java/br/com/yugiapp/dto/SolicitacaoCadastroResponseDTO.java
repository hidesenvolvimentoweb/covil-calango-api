package br.com.yugiapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitacaoCadastroResponseDTO {

    private Long id;
    private String nome;
    private String contato;
    private String email;
    private LocalDateTime dataHora;
}
