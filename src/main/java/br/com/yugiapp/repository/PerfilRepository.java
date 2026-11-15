package br.com.yugiapp.repository;

import br.com.yugiapp.model.Perfil;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerfilRepository extends JpaRepository<Perfil, Long>, JpaSpecificationExecutor<Perfil> {

    List<Perfil> findAllByPermissoes_Id(Long permissaoId);

    /** Perfis com as permissões já carregadas (usado a cada requisição autenticada). */
    @EntityGraph(attributePaths = "permissoes")
    List<Perfil> findAllByNameIn(List<String> names);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
