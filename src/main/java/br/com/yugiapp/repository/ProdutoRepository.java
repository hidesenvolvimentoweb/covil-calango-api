package br.com.yugiapp.repository;

import br.com.yugiapp.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * As instruções de estoque são SQL nativo porque a coluna ESTOQUE não é atualizada pela entidade
 * (ver Produto#estoque). {@code clearAutomatically} descarta a cópia em memória, que ficaria desatualizada.
 */
@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long>, JpaSpecificationExecutor<Produto> {

    /**
     * Dá baixa no estoque em uma única instrução: só altera a linha se houver unidades suficientes, então dois
     * pedidos simultâneos nunca vendem a mesma unidade. Devolve 0 quando o estoque não basta.
     * Produtos sem controle de estoque (nulo) não são alterados e também devolvem 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE tb_produto SET estoque = estoque - :quantidade "
            + "WHERE id = :id AND estoque IS NOT NULL AND estoque >= :quantidade", nativeQuery = true)
    int baixarEstoque(@Param("id") Long id, @Param("quantidade") int quantidade);

    /** Devolve ao estoque as unidades de um pedido cancelado; não altera produtos sem controle de estoque. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE tb_produto SET estoque = estoque + :quantidade WHERE id = :id AND estoque IS NOT NULL",
            nativeQuery = true)
    int devolverEstoque(@Param("id") Long id, @Param("quantidade") int quantidade);

    /** Ajuste feito pelo administrador: define o estoque (nulo para deixar de controlá-lo). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE tb_produto SET estoque = :estoque WHERE id = :id", nativeQuery = true)
    int definirEstoque(@Param("id") Long id, @Param("estoque") Integer estoque);
}
