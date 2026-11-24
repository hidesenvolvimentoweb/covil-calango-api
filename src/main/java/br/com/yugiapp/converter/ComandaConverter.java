package br.com.yugiapp.converter;

import br.com.yugiapp.dto.ComandaResponseDTO;
import br.com.yugiapp.dto.UsuarioConvidadoResponseDTO;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.service.ComandaService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ComandaConverter {

    public ComandaService comandaService;

    public ComandaResponseDTO toResponseDTO(Comanda entity) {
        return ComandaResponseDTO.builder()
                // Comandas de visitante não têm usuário: o nome vai em nomeCliente. O app exibe usuario.nome
                // ("Olá, Fulano!"), por isso o nome do cliente também segue nesse campo.
                .usuario(usuarioDaComanda(entity))
                .nomeCliente(entity.getNomeCliente())
                .numero(entity.getNumero())
                .dataHoraEntrada(entity.getDataHoraEntrada())
                .dataHoraSaida(entity.getDataHoraSaida())
                .status(entity.getStatus())
                .formaPagamento(entity.getFormaPagamento())
                .valorTotal(comandaService.obterValorTotalDaComanda(entity)).build();
    }

    /** O nome informado na comanda tem prioridade: as comandas antigas apontam para o usuário genérico "Visitante". */
    private static UsuarioConvidadoResponseDTO usuarioDaComanda(Comanda entity) {
        String nome = entity.getNomeCliente() != null && !entity.getNomeCliente().isBlank()
                ? entity.getNomeCliente()
                : entity.getUsuario() == null ? null : entity.getUsuario().getNome();
        return nome == null ? null : UsuarioConvidadoResponseDTO.builder().nome(nome).build();
    }
}
