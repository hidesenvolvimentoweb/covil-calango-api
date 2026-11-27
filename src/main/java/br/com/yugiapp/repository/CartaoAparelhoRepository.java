package br.com.yugiapp.repository;

import br.com.yugiapp.model.CartaoAparelho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartaoAparelhoRepository extends JpaRepository<CartaoAparelho, Long> {

    Optional<CartaoAparelho> findByChaveHashAndGateway(String chaveHash, String gateway);
}
