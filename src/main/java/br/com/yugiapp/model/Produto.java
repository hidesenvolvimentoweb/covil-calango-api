package br.com.yugiapp.model;

import br.com.yugiapp.enums.CategoriaProdutoEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_PRODUTO")
public class Produto implements Serializable {

    /** Máximo de unidades de um produto em um único pedido. */
    public static final long QUANTIDADE_MAXIMA_POR_PEDIDO = 20;

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NOME")
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORIA")
    private CategoriaProdutoEnum categoria;

    @Column(name = "IMAGEM")
    private String imagem;

    @Column(name = "VALOR")
    private Double valor;

    @Column(name = "INGREDIENTES")
    private String ingredientes;

    @Column(name = "PRAZO")
    private Long prazo;

    /** Preparado na cozinha (hambúrgueres, batatas, combos...): só pode ser pedido com a cozinha aberta. */
    @Builder.Default
    @Column(name = "USA_COZINHA", nullable = false)
    private boolean usaCozinha = false;

    /**
     * Unidades disponíveis para venda; nulo quando o produto não tem controle de estoque. Nunca é enviado ao cliente
     * (a entidade é devolvida pelo cardápio do app): ele recebe apenas {@link #getQuantidadeMaximaPedido()}.
     * <p>
     * Fora do cadastro, só muda pelas instruções do ProdutoRepository (baixa, devolução e ajuste): salvar a entidade
     * por outro motivo nunca sobrescreve uma venda feita ao mesmo tempo.
     */
    @JsonIgnore
    @Column(name = "ESTOQUE", updatable = false)
    private Integer estoque;

    /**
     * Quantidade que o cliente pode pedir de uma vez: o limite por pedido, reduzido ao estoque quando ele é menor.
     * Zero indica produto esgotado.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public long getQuantidadeMaximaPedido() {
        return estoque == null ? QUANTIDADE_MAXIMA_POR_PEDIDO : Math.min(estoque, QUANTIDADE_MAXIMA_POR_PEDIDO);
    }
}
