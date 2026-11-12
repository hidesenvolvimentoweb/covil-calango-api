package br.com.yugiapp.repository;

import br.com.yugiapp.model.SolicitacaoCadastro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SolicitacaoCadastroRepository extends JpaRepository<SolicitacaoCadastro, Long>, JpaSpecificationExecutor<SolicitacaoCadastro> {

    boolean existsByEmailIgnoreCase(String email);
}
