package br.com.yugiapp.repository;

import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pagamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    /** Bloqueia o pagamento até o fim da transação (webhook e reconciliação podem chegar ao mesmo tempo). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pagamento p where p.id = :id")
    Optional<Pagamento> findByIdParaAtualizacao(@Param("id") Long id);

    @Query("select p.id from Pagamento p where p.gateway = :gateway and p.gatewayId = :gatewayId")
    Optional<Long> findIdByGatewayAndGatewayId(@Param("gateway") String gateway, @Param("gatewayId") String gatewayId);

    @Query("select p.comanda.numero from Pagamento p where p.id = :id")
    Optional<String> findNumeroComandaById(@Param("id") Long id);

    Optional<Pagamento> findFirstByComandaAndStatusOrderByIdDesc(Comanda comanda, StatusPagamentoEnum status);

    Optional<Pagamento> findFirstByComandaNumeroOrderByIdDesc(String numero);

    /** Último pagamento de cada comanda informada, em uma única consulta (listagem de comandas). */
    @Query("select p from Pagamento p where p.comanda.numero in :numeros"
            + " and p.id = (select max(p2.id) from Pagamento p2 where p2.comanda = p.comanda)")
    List<Pagamento> findUltimosByComandaNumeroIn(@Param("numeros") Collection<String> numeros);

    boolean existsByComandaNumeroAndStatus(String numero, StatusPagamentoEnum status);

    @Query("select p.id from Pagamento p where p.status = :status and p.dataHoraCriacao < :limite order by p.id")
    List<Long> findIdsByStatusCriadosAntesDe(@Param("status") StatusPagamentoEnum status,
                                             @Param("limite") LocalDateTime limite);
}
