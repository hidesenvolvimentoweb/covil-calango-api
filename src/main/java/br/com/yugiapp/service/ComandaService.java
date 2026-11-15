package br.com.yugiapp.service;

import br.com.yugiapp.converter.ComandaConverter;
import br.com.yugiapp.dto.ComandaFilterRequestDTO;
import br.com.yugiapp.dto.ComandaResponseDTO;
import br.com.yugiapp.dto.PedidoFilterRequestDTO;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.model.Usuario;
import br.com.yugiapp.repository.ComandaRepository;
import br.com.yugiapp.specs.ComandaSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor(onConstructor_ = { @Lazy })
public class ComandaService {

    private final ComandaRepository comandaRepository;
    private final ComandaSpecifications comandaSpecifications;
    private final ComandaConverter comandaConverter;
    private final UsuarioService usuarioService;
    private final PedidoService pedidoService;
    /** Pedidos que entram no valor da comanda: todos, exceto os cancelados. */
    private static final List<String> STATUS_COBRADOS = List.of(
            StatusPedidoEnum.SOLICITADO.name(),
            StatusPedidoEnum.EM_ANDAMENTO.name(),
            StatusPedidoEnum.PRONTO.name(),
            StatusPedidoEnum.FINALIZADO.name());

    public List<ComandaResponseDTO> getAllByFilters(ComandaFilterRequestDTO pedidoFilterRequestDTO) {
        return comandaRepository.findAll(comandaSpecifications.getSpecs(pedidoFilterRequestDTO))
                .stream()
                .map(comandaConverter::toResponseDTO)
                .toList();
    }

    public Comanda getByNumero(String numero) {
        return comandaRepository.findByNumero(numero).orElseThrow();
    }

    public Optional<Comanda> findByNumero(String numero) {
        return comandaRepository.findByNumero(numero);
    }

    public ComandaResponseDTO getResponseDTOByNumero(String numero) {
        Comanda comanda = comandaRepository.findByNumero(numero).orElseThrow();
        ComandaResponseDTO comandaResponseDTO = comandaConverter.toResponseDTO(comanda);
        comandaResponseDTO.setPedidos(pedidoService.getAllResponseDTOByFilters(PedidoFilterRequestDTO.builder().comanda(numero).build()));
        comandaResponseDTO.setValorTotal(obterValorTotalDaComanda(comanda));
        return comandaResponseDTO;
    }

    public ComandaResponseDTO gerarComandaDTO(String nome) {
        Usuario usuario = Usuario.builder().id(1L).nome(nome).build();
        Comanda comandaCompleta = gerarComanda(usuario);
        return ComandaResponseDTO.builder()
                .numero(comandaCompleta.getNumero())
                .dataHoraEntrada(comandaCompleta.getDataHoraEntrada())
                .nomeCliente(comandaCompleta.getNomeCliente())
                .usuario(usuarioService.criarUsuarioConvidado(usuario.getNome(), comandaCompleta.getNumero()))
                .build();
    }

    public Comanda gerarComanda(Usuario usuario) {
        Comanda comandaSemNumero = comandaRepository.save(
                Comanda.builder()
                        .dataHoraEntrada(LocalDateTime.now())
                        .usuario(usuario)
                        .nomeCliente(usuario.getNome())
                        .status(StatusComandaEnum.EM_ANALISE.getValue())
                        .build());
        LocalDateTime agora = LocalDateTime.now();
        comandaSemNumero.setNumero(String.valueOf(agora.getYear()) + agora.getDayOfMonth() + "-" + comandaSemNumero.getId());
        return comandaRepository.save(comandaSemNumero);
    }

    public Comanda alterarStatus(String numero, StatusComandaEnum statusComandaEnum) {
        Comanda comandaAlterada = comandaRepository.findByNumero(numero).orElseThrow();
        if (StatusComandaEnum.FECHADA.getValue().equals(statusComandaEnum.getValue())) {
            comandaAlterada.setDataHoraSaida(LocalDateTime.now());
        }
        if (StatusComandaEnum.ABERTA.getValue().equals(statusComandaEnum.getValue())) {
            comandaAlterada.setDataHoraEntrada(LocalDateTime.now());
        }
        comandaAlterada.setStatus(statusComandaEnum.getValue());
        return save(comandaAlterada);
    }

    public Double obterValorTotalDaComanda(Comanda comanda) {
        PedidoFilterRequestDTO filtros = PedidoFilterRequestDTO.builder().comanda(comanda.getNumero())
                .status(STATUS_COBRADOS).build();
        List<Pedido> pedidosDaComanda = pedidoService.getAllByFilters(filtros);
        return pedidosDaComanda.stream().map(p -> (p.getProduto().getValor()) * p.getQuantidade()).reduce(0.0, Double::sum);
    }

    public Comanda save(Comanda jogador) {
        return comandaRepository.save(jogador);
    }

}
