package br.com.yugiapp.repository;

import br.com.yugiapp.model.Comanda;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComandaRepository extends JpaRepository<Comanda, Long> {

    Optional<Comanda> findByNumero(String numero);

    /** Bloqueia a comanda até o fim da transação (serializa as operações de pagamento da mesma comanda). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comanda c where c.numero = :numero")
    Optional<Comanda> findByNumeroParaAtualizacao(@Param("numero") String numero);

    List<Comanda> findAll(Specification<Comanda> specification);

    /** Comandas que entraram no período [inicio, fim) (relatórios). */
    @Query("select c from Comanda c where c.dataHoraEntrada >= :inicio and c.dataHoraEntrada < :fim")
    List<Comanda> findEntradasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Comandas fechadas no período [inicio, fim): é nelas que o valor foi arrecadado (relatórios). */
    @Query("select c from Comanda c where c.status = :status and c.dataHoraSaida >= :inicio and c.dataHoraSaida < :fim")
    List<Comanda> findFechadasNoPeriodo(@Param("status") String status, @Param("inicio") LocalDateTime inicio,
                                        @Param("fim") LocalDateTime fim);
}
