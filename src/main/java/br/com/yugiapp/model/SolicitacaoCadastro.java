package br.com.yugiapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * Solicitação de cadastro de um novo usuário do web. Só se torna um registro em TB_USUARIO
 * depois que um administrador finaliza o cadastro.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_SOLICITACAO_CADASTRO")
public class SolicitacaoCadastro {

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NOME")
    private String nome;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "CONTATO")
    private String contato;

    @Column(name = "DT_HORA")
    private LocalDateTime dataHora;
}
