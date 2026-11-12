package br.com.yugiapp.model;

import br.com.yugiapp.enums.TipoParametroEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_PARAMETRO")
public class Parametro {

    @Id
    @Column(name = "ID")
    @SequenceGenerator(name = "parametroIdGenerator", sequenceName = "tb_parametro_seq", schema = "public", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "parametroIdGenerator")
    private Long id;

    @Column(name = "NOME")
    private String nome;

    @Column(name = "DESCRICAO", columnDefinition = "TEXT")
    private String descricao;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, length = 20)
    private TipoParametroEnum tipo = TipoParametroEnum.TEXTO;

}
