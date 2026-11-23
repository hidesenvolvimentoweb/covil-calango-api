package br.com.yugiapp.service;

import br.com.yugiapp.converter.ComandaConverter;
import br.com.yugiapp.dto.ComandaFilterRequestDTO;
import br.com.yugiapp.dto.ComandaResponseDTO;
import br.com.yugiapp.dto.PagamentoResponseDTO;
import br.com.yugiapp.repository.PagamentoRepository;
import br.com.yugiapp.dto.PedidoFilterRequestDTO;
import br.com.yugiapp.enums.FormaPagamentoEnum;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.model.Usuario;
import br.com.yugiapp.repository.ComandaRepository;
import br.com.yugiapp.specs.ComandaSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor(onConstructor_ = { @Lazy })
public class ComandaService {

    private final ComandaRepository comandaRepository;
    private final ComandaSpecifications comandaSpecifications;
    private final ComandaConverter comandaConverter;
    private final UsuarioService usuarioService;
    private final PedidoService pedidoService;
    private final PagamentoRepository pagamentoRepository;
    /** Pedidos que entram no valor da comanda: todos, exceto os cancelados. */
    private static final List<String> STATUS_COBRADOS = List.of(
            StatusPedidoEnum.SOLICITADO.name(),
            StatusPedidoEnum.EM_ANDAMENTO.name(),
            StatusPedidoEnum.PRONTO.name(),
            StatusPedidoEnum.FINALIZADO.name());

    public List<ComandaResponseDTO> getAllByFilters(ComandaFilterRequestDTO pedidoFilterRequestDTO) {
        List<ComandaResponseDTO> comandas = comandaRepository.findAll(comandaSpecifications.getSpecs(pedidoFilterRequestDTO))
                .stream()
                .map(comandaConverter::toResponseDTO)
                .toList();
        preencherUltimoPagamento(comandas);
        return comandas;
    }

    /** Status do pagamento pelo app em cada comanda (ex.: PIX pendente), buscado de uma vez para a lista toda. */
    private void preencherUltimoPagamento(List<ComandaResponseDTO> comandas) {
        Set<String> numeros = comandas.stream().map(ComandaResponseDTO::getNumero).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (numeros.isEmpty()) {
            return;
        }
        Map<String, PagamentoResponseDTO> pagamentos = pagamentoRepository.findUltimosByComandaNumeroIn(numeros).stream()
                .collect(Collectors.toMap(p -> p.getComanda().getNumero(), PagamentoResponseDTO::resumo, (a, b) -> a));
        comandas.forEach(comanda -> comanda.setUltimoPagamento(pagamentos.get(comanda.getNumero())));
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
        comandaResponseDTO.setUltimoPagamento(pagamentoRepository.findFirstByComandaNumeroOrderByIdDesc(numero)
                .map(PagamentoResponseDTO::resumo).orElse(null));
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

    /**
     * Fecha a comanda registrando como ela foi paga. A comanda fica bloqueada até o fim da transação: se o PIX do app
     * for aprovado ao mesmo tempo que o fechamento no balcão, só um dos dois fecha (o outro recebe 409).
     */
    @Transactional
    public Comanda fechar(String numero, FormaPagamentoEnum formaPagamento) {
        if (formaPagamento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a forma de pagamento da comanda");
        }
        Comanda comanda = comandaRepository.findByNumeroParaAtualizacao(numero).orElseThrow();
        if (StatusComandaEnum.FECHADA.getValue().equals(comanda.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A comanda " + numero + " já está fechada");
        }
        comanda.setFormaPagamento(formaPagamento);
        comanda.setDataHoraSaida(LocalDateTime.now());
        comanda.setStatus(StatusComandaEnum.FECHADA.getValue());
        return save(comanda);
    }

    public Double obterValorTotalDaComanda(Comanda comanda) {
        return calcularValorTotal(comanda).doubleValue();
    }

    /**
     * Valor da comanda em reais, com 2 casas decimais. Usa BigDecimal porque a soma em double acumula erros de
     * arredondamento (ex.: 29.999999), e o provedor de pagamento exige o valor exato.
     */
    public BigDecimal calcularValorTotal(Comanda comanda) {
        PedidoFilterRequestDTO filtros = PedidoFilterRequestDTO.builder().comanda(comanda.getNumero())
                .status(STATUS_COBRADOS).build();
        List<Pedido> pedidosDaComanda = pedidoService.getAllByFilters(filtros);
        return pedidosDaComanda.stream()
                .map(p -> BigDecimal.valueOf(p.getProduto().getValor()).multiply(BigDecimal.valueOf(p.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Comanda save(Comanda jogador) {
        return comandaRepository.save(jogador);
    }

}
