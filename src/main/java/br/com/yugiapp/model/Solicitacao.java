package br.com.yugiapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_SOLICITACAO")
public class Solicitacao {

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** requestId do app: identifica o tópico de resposta /topic/comanda/{sessionId}, por isso nunca sai no JSON. */
    @JsonIgnore
    @Column(name = "SESSIONID")
    private String sessionId;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "NOME")
    private String nome;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "DT_HORA")
    private LocalDateTime dataHora;

    @Column(name = "CELULAR")
    private String celular;

    @Column(name = "NOTIFICAR_WHATSAPP", nullable = false)
    private boolean notificarWhatsapp;

    @ManyToOne
    @JoinColumn(name = "FK_COMANDA")
    private Comanda comanda;

    /**
     * Token do visitante, enviado apenas ao app que fez a solicitação (tópico /topic/comanda/{sessionId})
     * quando ela é aceita. Não é gravado no banco.
     */
    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String token;
}
