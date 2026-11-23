package br.com.yugiapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_USUARIO")
public class Usuario implements Serializable {

    @Id
    @Column(name = "ID")
    @SequenceGenerator(name = "usuarioIdGenerator", sequenceName = "tb_usuario_seq", schema = "public", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "usuarioIdGenerator")
    private Long id;

    @Column(name = "NOME")
    private String nome;

    /*
     * A entidade chega ao JSON aninhada em pedidos e comandas (rotas e tópicos públicos). Somente id e nome
     * são expostos; os dados de cadastro e acesso saem apenas pelos DTOs das telas administrativas.
     */
    @JsonIgnore
    @Column(name = "CONTATO")
    private String contato;

    @JsonIgnore
    @Column(unique = true)
    private String email;

    /** Sempre o hash BCrypt da senha; nunca a senha em texto puro. */
    @JsonIgnore
    @ToString.Exclude
    private String password;

    /** Indica que a senha atual é a temporária gerada na finalização do cadastro e ainda precisa ser trocada. */
    @JsonIgnore
    @Column(name = "SENHA_TEMPORARIA")
    private boolean senhaTemporaria;

    @JsonIgnore
    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @JoinTable(name = "tb_usuario_perfil",
            joinColumns = @JoinColumn(name = "id_usuario"),
            inverseJoinColumns = @JoinColumn(name = "id_perfil"))
    private List<Perfil> perfis;

    /**
     * Última troca de senha ou de perfis. As sessões (JWT) emitidas antes deste momento são recusadas
     * (ver SessaoUsuarioService); nulo quando nada mudou.
     */
    @JsonIgnore
    @Column(name = "ACESSO_ALTERADO_EM")
    private LocalDateTime acessoAlteradoEm;

    /** Encerra todas as sessões abertas do usuário: quem estava logado precisa entrar novamente. */
    public void encerrarSessoes() {
        // O JWT guarda a emissão em segundos; truncar evita recusar o login feito logo depois, no mesmo segundo.
        this.acessoAlteradoEm = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
