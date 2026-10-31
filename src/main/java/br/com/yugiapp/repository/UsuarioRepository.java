package br.com.yugiapp.repository;

import br.com.yugiapp.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

   Optional<Usuario> findByEmail(String email);

   boolean existsByEmailAndIdNot(String email, Long id);

   List<Usuario> findAllByPerfis_Id(Long perfilId);
}
