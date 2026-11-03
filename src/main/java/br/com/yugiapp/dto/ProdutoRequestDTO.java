package br.com.yugiapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class ProdutoRequestDTO {

    private Long id;
    private String nome;
    private String categoria;
    private String imagem;
    private Double valor;
    private String ingredientes;
    private Long prazo;
}
