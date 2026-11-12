package br.com.yugiapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * Pedido de recuperação de senha. O token enviado por e-mail não é armazenado, apenas o seu hash (SHA-256).
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_RECUPERACAO_SENHA")
public class RecuperacaoSenha {

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "FK_USUARIO")
    private Long usuarioId;

    @Column(name = "TOKEN_HASH")
    private String tokenHash;

    @Column(name = "DT_HORA_EXPIRACAO")
    private LocalDateTime dataHoraExpiracao;
}
