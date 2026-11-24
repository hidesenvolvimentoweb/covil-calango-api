package br.com.yugiapp.repository;

import br.com.yugiapp.model.Evento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {

    /**
     * Eventos que acontecem, ao menos em parte, no período [inicio, fim): começam antes do fim do período e terminam
     * depois do início dele (ex.: um torneio que vira a noite aparece nos dois dias).
     */
    @Query("select e from Evento e where e.dataHoraInicio < :fim and e.dataHoraFim > :inicio order by e.dataHoraInicio, e.id")
    List<Evento> findNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /** Bloqueia o evento até o fim da transação: duas inscrições simultâneas não ultrapassam as vagas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Evento e where e.id = :id")
    Optional<Evento> findByIdParaAtualizacao(@Param("id") Long id);
}
