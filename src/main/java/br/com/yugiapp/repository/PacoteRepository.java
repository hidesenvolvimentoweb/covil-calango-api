package br.com.yugiapp.repository;

import br.com.yugiapp.enums.StatusPacoteEnum;
import br.com.yugiapp.model.Pacote;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PacoteRepository extends JpaRepository<Pacote, Long> {

    boolean existsByCodigo(String codigo);

    Optional<Pacote> findByCodigo(String codigo);

    /** Bloqueia o pacote: duas baixas (ou baixa e cancelamento) simultâneas não passam juntas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pacote p where p.id = :id")
    Optional<Pacote> findByIdParaAtualizacao(@Param("id") Long id);

    List<Pacote> findByComandaNumeroOrderByDataHoraDepositoDescIdDesc(String numeroComanda);

    List<Pacote> findByStatusInOrderByDataHoraDepositoDescIdDesc(Collection<StatusPacoteEnum> status, Pageable pageable);

    /** Excluir o pedido da taxa pela tela da comanda: o pacote continua, sem cobrança. */
    @Modifying
    @Query("update Pacote p set p.pedido = null where p.pedido.id = :pedido")
    int desvincularPedido(@Param("pedido") Long pedidoId);
}
