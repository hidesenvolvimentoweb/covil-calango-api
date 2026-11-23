package br.com.yugiapp.repository;

import br.com.yugiapp.model.Pedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query(value = "SELECT p from Pedido p where p.status = 'EM_ANDAMENTO'")
    List<Pedido> findAllPendentes();

    List<Pedido> findAll(Specification<Pedido> specification);

    /** Bloqueia o pedido até o fim da transação, para que duas alterações simultâneas não devolvam o estoque duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> findByIdParaAtualizacao(@Param("id") Long id);
}
