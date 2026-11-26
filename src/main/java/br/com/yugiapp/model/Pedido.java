package br.com.yugiapp.model;

import br.com.yugiapp.enums.StatusPedidoEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_PEDIDO")
public class Pedido implements Serializable {

    @Id
    @Column(name = "ID")
    @SequenceGenerator(name = "pedidoIdGenerator", sequenceName = "tb_pedido_seq", schema = "public", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pedidoIdGenerator")
    private Long id;

    @Column(name = "STATUS")
    @Enumerated(EnumType.STRING)
    private StatusPedidoEnum status;

    @Column(name = "DATA_HORA")
    private LocalDateTime dataHora;

    @Column(name = "OBSERVACAO")
    private String observacao;

    /** Nulo nos itens que não são produtos do cardápio, como a inscrição em um evento (ver {@link #descricao}). */
    @ManyToOne
    @JoinColumn(name = "FK_PRODUTO")
    private Produto produto;

    /** Nome do item sem produto, gravado na criação (ex.: "Evento - Torneio Locals"). */
    @Column(name = "DESCRICAO")
    private String descricao;

    @ManyToOne
    @JoinColumn(name = "FK_COMANDA")
    private Comanda comanda;

    @Column(name = "QUANTIDADE")
    private Long quantidade;

    /**
     * Preço unitário do produto no momento do pedido. Mudar o preço do produto depois não altera os pedidos já
     * feitos (nem as comandas fechadas e os relatórios).
     */
    @Column(name = "VALOR_UNITARIO")
    private Double valorUnitario;

    /** Item sem produto (inscrição em evento, taxa de pacote): entra no valor da comanda, mas não é da cozinha. */
    public boolean itemAvulso() {
        return produto == null && descricao != null;
    }

    /** Nome exibido na comanda: o do produto ou, sem produto, a descrição gravada. */
    public String nomeDoItem() {
        return produto != null ? produto.getNome() : descricao;
    }

    /** Preço cobrado por unidade: o gravado no pedido ou, na falta dele, o preço atual do produto. */
    public Double precoUnitarioCobrado() {
        if (valorUnitario != null) {
            return valorUnitario;
        }
        return produto == null ? null : produto.getValor();
    }

    /** Valor do pedido (preço cobrado x quantidade), em BigDecimal para somar sem erros de arredondamento. */
    public BigDecimal valorTotal() {
        Double preco = precoUnitarioCobrado();
        if (preco == null || quantidade == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(preco).multiply(BigDecimal.valueOf(quantidade));
    }
}
