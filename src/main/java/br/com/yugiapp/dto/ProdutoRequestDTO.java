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
    /** Unidades disponíveis para venda; nulo para não controlar o estoque. */
    private Integer estoque;
    /**
     * Na alteração, o estoque só é gravado quando este campo é verdadeiro. Assim, salvar o produto por outro motivo
     * (ex.: preço) não devolve ao estoque as unidades vendidas desde que o formulário foi aberto.
     * No cadastro, o estoque informado é sempre gravado.
     */
    private Boolean alterarEstoque;
}
