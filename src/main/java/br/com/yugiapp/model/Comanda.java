package br.com.yugiapp.model;

import br.com.yugiapp.enums.FormaPagamentoEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_COMANDA")
public class Comanda implements Serializable {

    @Id
    @Column(name = "ID")
    @SequenceGenerator(name = "comandaIdGenerator", sequenceName = "tb_comanda_seq", schema = "public", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "comandaIdGenerator")
    private Long id;

    @Column(name = "NUMERO")
    private String numero;

    /** Nulo nas comandas de visitante (o nome fica em {@link #nomeCliente}); no JSON, ver {@link #getUsuarioExibido()}. */
    @JsonIgnore
    @Getter(onMethod_ = @JsonIgnore)
    @ManyToOne
    @JoinColumn(name = "FK_USUARIO")
    private Usuario usuario;

    @Column(name = "NOME_CLIENTE")
    private String nomeCliente;

    @Column(name = "DT_HORA_ENTRADA")
    private LocalDateTime dataHoraEntrada;

    @Column(name = "DT_HORA_SAIDA")
    private LocalDateTime dataHoraSaida;

    @Column(name = "STATUS")
    private String status;

    /** Como a comanda foi paga; preenchida no fechamento (nula enquanto aberta). */
    @Enumerated(EnumType.STRING)
    @Column(name = "FORMA_PAGAMENTO")
    private FormaPagamentoEnum formaPagamento;

    /*
     * A entidade chega ao JSON aninhada nos pedidos (tópicos do WebSocket e rotas do app). O celular é dado pessoal
     * e só é usado internamente, para as notificações do WhatsApp.
     */
    /** Celular (somente dígitos, com DDI) que recebe as notificações dos pedidos pelo WhatsApp. */
    @JsonIgnore
    @Column(name = "CELULAR")
    private String celular;

    /** Consentimento do cliente para receber as notificações dos pedidos pelo WhatsApp. */
    @JsonIgnore
    @Column(name = "NOTIFICAR_WHATSAPP", nullable = false)
    private boolean notificarWhatsapp;

    /**
     * "usuario" no JSON (tópicos do WebSocket, solicitação aceita): o app exibe usuario.nome ("Olá, Fulano!"). As
     * comandas de visitante não têm usuário e as antigas apontam para o genérico "Visitante", por isso o nome
     * informado na comanda tem prioridade.
     */
    @JsonProperty("usuario")
    public Usuario getUsuarioExibido() {
        String nome = nomeCliente != null && !nomeCliente.isBlank() ? nomeCliente
                : usuario == null ? null : usuario.getNome();
        if (nome == null) {
            return null;
        }
        return Usuario.builder().id(usuario == null ? null : usuario.getId()).nome(nome).build();
    }

}
