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
                // Comandas de visitante não têm usuário (o nome vai em nomeCliente); as antigas apontam para o "Visitante".
                .usuario(entity.getUsuario() == null ? null
                        : UsuarioConvidadoResponseDTO.builder().nome(entity.getUsuario().getNome()).build())
                .nomeCliente(entity.getNomeCliente())
                .numero(entity.getNumero())
                .dataHoraEntrada(entity.getDataHoraEntrada())
                .dataHoraSaida(entity.getDataHoraSaida())
                .status(entity.getStatus())
                .formaPagamento(entity.getFormaPagamento())
                .valorTotal(comandaService.obterValorTotalDaComanda(entity)).build();
    }
}
