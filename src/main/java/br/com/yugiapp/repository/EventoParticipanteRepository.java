package br.com.yugiapp.repository;

import br.com.yugiapp.model.EventoParticipante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventoParticipanteRepository extends JpaRepository<EventoParticipante, Long> {

    List<EventoParticipante> findByEventoIdOrderByDataHoraInscricaoAscIdAsc(Long eventoId);

    Optional<EventoParticipante> findByEventoIdAndComandaNumero(Long eventoId, String numeroComanda);

    Optional<EventoParticipante> findByIdAndEventoId(Long id, Long eventoId);

    long countByEventoId(Long eventoId);

    boolean existsByEventoId(Long eventoId);

    /** Total de participantes de cada evento: linhas [id do evento, quantidade]. */
    @Query("select p.evento.id, count(p) from EventoParticipante p where p.evento.id in :eventos group by p.evento.id")
    List<Object[]> contarPorEvento(@Param("eventos") Collection<Long> eventos);

    /** Eventos, entre os informados, em que a comanda está inscrita. */
    @Query("select p.evento.id from EventoParticipante p where p.comanda.numero = :numero and p.evento.id in :eventos")
    List<Long> findEventosDaComanda(@Param("numero") String numeroComanda, @Param("eventos") Collection<Long> eventos);

    /** Excluir o pedido da inscrição (pela tela da comanda) também cancela a participação no evento. */
    @Modifying
    @Query("delete from EventoParticipante p where p.pedido.id = :pedido")
    int deleteByPedidoId(@Param("pedido") Long pedidoId);

    /** Excluir um evento já encerrado apaga as participações (as inscrições cobradas continuam nas comandas). */
    @Modifying
    @Query("delete from EventoParticipante p where p.evento.id = :evento")
    int deleteByEventoId(@Param("evento") Long eventoId);
}
