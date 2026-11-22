package br.com.yugiapp.repository;

import br.com.yugiapp.model.Comanda;
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
public interface ComandaRepository extends JpaRepository<Comanda, Long> {

    Optional<Comanda> findByNumero(String numero);

    /** Bloqueia a comanda até o fim da transação (serializa as operações de pagamento da mesma comanda). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comanda c where c.numero = :numero")
    Optional<Comanda> findByNumeroParaAtualizacao(@Param("numero") String numero);

    List<Comanda> findAll(Specification<Comanda> specification);
}
