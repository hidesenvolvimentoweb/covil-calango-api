package br.com.yugiapp.dto;

import br.com.yugiapp.enums.CategoriaProdutoEnum;
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
    private CategoriaProdutoEnum categoria;
    private String imagem;
    private Double valor;
    private String ingredientes;
    private Long prazo;
    /** Unidades disponíveis; nulo quando o produto não tem controle de estoque. Só exposto nas rotas de administração. */
    private Integer estoque;
}
