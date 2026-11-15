package br.com.yugiapp.model;

import br.com.yugiapp.enums.CategoriaProdutoEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Data
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "TB_PRODUTO")
public class Produto implements Serializable {

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NOME")
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORIA")
    private CategoriaProdutoEnum categoria;

    @Column(name = "IMAGEM")
    private String imagem;

    @Column(name = "VALOR")
    private Double valor;

    @Column(name = "INGREDIENTES")
    private String ingredientes;

    @Column(name = "PRAZO")
    private Long prazo;
}
