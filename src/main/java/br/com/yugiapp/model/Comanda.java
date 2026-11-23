package br.com.yugiapp.model;

import br.com.yugiapp.enums.FormaPagamentoEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
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

}
