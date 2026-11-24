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
import br.com.yugiapp.repository.ComandaRepository;
import br.com.yugiapp.specs.ComandaSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    /** Fora do construtor por causa do @Lazy (como o fuso). */
    @PersistenceContext
    private EntityManager entityManager;

    private static final String FUSO_PADRAO = "America/Sao_Paulo";
    /** Fuso do estabelecimento, o mesmo dos relatórios (app.fuso-horario). Fora do construtor por causa do @Lazy. */
    @Value("${app.fuso-horario:" + FUSO_PADRAO + "}")
    private String fusoHorario;

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

    /**
     * Comanda bloqueada até o fim da transação. O fechamento e a geração do PIX também bloqueiam a comanda: um
     * pedido ou uma inscrição em evento nunca entra numa comanda que está sendo fechada ou cobrada ao mesmo tempo.
     */
    public Comanda travarParaAtualizacao(String numero) {
        Comanda comanda = comandaRepository.findByNumeroParaAtualizacao(numero)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comanda não encontrada"));
        // Se a comanda já estava carregada na transação, a consulta com bloqueio devolve a cópia em memória:
        // relê do banco para decidir com o status atual (ex.: fechada no instante anterior ao bloqueio).
        if (entityManager != null) {
            entityManager.refresh(comanda);
        }
        return comanda;
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
        Comanda comandaCompleta = gerarComanda(nome);
        return ComandaResponseDTO.builder()
                .numero(comandaCompleta.getNumero())
                .dataHoraEntrada(comandaCompleta.getDataHoraEntrada())
                .nomeCliente(comandaCompleta.getNomeCliente())
                .usuario(usuarioService.criarUsuarioConvidado(nome, comandaCompleta.getNumero()))
                .build();
    }

    /**
     * Cria a comanda de um cliente visitante. Ela não fica ligada a nenhum usuário cadastrado: o nome do cliente vai
     * em {@code nomeCliente} (as comandas antigas apontavam para o usuário "Visitante", de id 1).
     */
    public Comanda gerarComanda(String nomeCliente) {
        Comanda comandaSemNumero = comandaRepository.save(
                Comanda.builder()
                        .dataHoraEntrada(LocalDateTime.now())
                        .nomeCliente(nomeCliente)
                        .status(StatusComandaEnum.EM_ANALISE.getValue())
                        .build());
        comandaSemNumero.setNumero(numeroDaComanda(comandaSemNumero.getId()));
        return comandaRepository.save(comandaSemNumero);
    }

    /** Ano e dia no fuso do estabelecimento (o da JVM é UTC no container: à noite já seria o dia seguinte). */
    String numeroDaComanda(Long id) {
        LocalDate hoje = LocalDate.now(fusoDoEstabelecimento());
        return String.valueOf(hoje.getYear()) + hoje.getDayOfMonth() + "-" + id;
    }

    private ZoneId fusoDoEstabelecimento() {
        return fusoHorario == null || fusoHorario.isBlank() ? ZoneId.of(FUSO_PADRAO) : ZoneId.of(fusoHorario);
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
                // Preço gravado em cada pedido: mudar o preço do produto não altera o que já foi pedido.
                .map(Pedido::valorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Comanda save(Comanda jogador) {
        return comandaRepository.save(jogador);
    }

}
