package br.com.yugiapp.repository;

import br.com.yugiapp.model.Solicitacao;
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
public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {

    /** A mais recente: bases antigas podem ter mais de uma solicitação com o mesmo id de sessão. */
    Optional<Solicitacao> findFirstBySessionIdOrderByIdDesc(String sessionId);

    boolean existsBySessionId(String sessionId);

    List<Solicitacao> findAll(Specification<Solicitacao> specification);

    /**
     * Bloqueia a solicitação até o fim da transação: dois atendentes respondendo ao mesmo tempo são atendidos um de
     * cada vez, e o segundo já encontra a solicitação respondida.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Solicitacao s where s.id = :id")
    Optional<Solicitacao> findByIdParaAtualizacao(@Param("id") Long id);
}
