package br.com.yugiapp.dto;

import br.com.yugiapp.enums.CategoriaProdutoEnum;
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
    private CategoriaProdutoEnum categoria;
    private String imagem;
    private Double valor;
    private String ingredientes;
    private Long prazo;
}
