package br.com.yugiapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoResponseDTO {

    private Long id;
    private String nome;
    private String categoria;
    private String imagem;
    private Double valor;
    private String ingredientes;
    private Long prazo;
}
