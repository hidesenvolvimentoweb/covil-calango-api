package br.com.yugiapp.repository;

import br.com.yugiapp.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

   Optional<Usuario> findByEmail(String email);

   boolean existsByEmailAndIdNot(String email, Long id);

   List<Usuario> findAllByPerfis_Id(Long perfilId);

   List<Usuario> findAllByPasswordIsNotNull();

   /** Dados mínimos para validar a sessão a cada requisição, sem carregar os perfis do usuário. */
   @Query("select u.id as id, u.acessoAlteradoEm as acessoAlteradoEm from Usuario u where u.email = :email")
   Optional<SessaoUsuario> findSessaoByEmail(@Param("email") String email);

   interface SessaoUsuario {
      Long getId();

      LocalDateTime getAcessoAlteradoEm();
   }
}
