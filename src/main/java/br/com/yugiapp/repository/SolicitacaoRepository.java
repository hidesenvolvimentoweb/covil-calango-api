package br.com.yugiapp.repository;

import br.com.yugiapp.model.Solicitacao;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {

    /** A mais recente: bases antigas podem ter mais de uma solicitação com o mesmo id de sessão. */
    Optional<Solicitacao> findFirstBySessionIdOrderByIdDesc(String sessionId);

    boolean existsBySessionId(String sessionId);

    List<Solicitacao> findAll(Specification<Solicitacao> specification);
}
