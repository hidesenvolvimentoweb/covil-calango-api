package br.com.yugiapp.service;

import br.com.yugiapp.dto.RelatorioDiarioDTO;
import br.com.yugiapp.dto.RelatorioDiarioDTO.Agrupamento;
import br.com.yugiapp.dto.RelatorioDiarioDTO.Contagem;
import br.com.yugiapp.dto.RelatorioDiarioDTO.Dia;
import br.com.yugiapp.dto.RelatorioDiarioDTO.Hora;
import br.com.yugiapp.dto.RelatorioDiarioDTO.ProdutoVendido;
import br.com.yugiapp.enums.CategoriaProdutoEnum;
import br.com.yugiapp.enums.FormaPagamentoEnum;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.model.Produto;
import br.com.yugiapp.repository.ComandaRepository;
import br.com.yugiapp.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Relatórios do Dashboard.
 * <p>
 * Fuso: as datas são gravadas em {@link LocalDateTime} no fuso da JVM (no container, normalmente UTC). O dia do
 * relatório é o dia no fuso do estabelecimento ({@code app.fuso-horario}); os limites são convertidos para o fuso da
 * JVM antes da consulta e cada horário volta para o fuso do estabelecimento ao ser agrupado por hora ou por dia.
 * Assim um pedido às 22h de Brasília conta no dia certo mesmo com a JVM em UTC.
 * <p>
 * Valores: como no restante da API (ComandaService.calcularValorTotal), o valor do pedido é o preço atual do produto
 * vezes a quantidade, e pedidos cancelados não contam.
 */
@Service
public class RelatorioService {

    static final int DIAS_TENDENCIA = 7;
    private static final String SEM_FORMA_PAGAMENTO = "NAO_INFORMADA";

    private final ComandaRepository comandaRepository;
    private final PedidoRepository pedidoRepository;
    private final ZoneId fusoEstabelecimento;
    private final ZoneId fusoDados;
    private final Clock clock;

    /** Construtor usado pelo Spring (com dois construtores, ele precisa da indicação explícita). */
    @Autowired
    public RelatorioService(ComandaRepository comandaRepository, PedidoRepository pedidoRepository,
                            @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(comandaRepository, pedidoRepository, ZoneId.of(fusoHorario), ZoneId.systemDefault(), Clock.systemUTC());
    }

    RelatorioService(ComandaRepository comandaRepository, PedidoRepository pedidoRepository,
                     ZoneId fusoEstabelecimento, ZoneId fusoDados, Clock clock) {
        this.comandaRepository = comandaRepository;
        this.pedidoRepository = pedidoRepository;
        this.fusoEstabelecimento = fusoEstabelecimento;
        this.fusoDados = fusoDados;
        this.clock = clock;
    }

    /** Hoje no fuso do estabelecimento. */
    public LocalDate hoje() {
        return LocalDate.now(clock.withZone(fusoEstabelecimento));
    }

    @Transactional(readOnly = true)
    public RelatorioDiarioDTO gerarDiario(LocalDate dia) {
        LocalDateTime inicio = inicioDoDia(dia);
        LocalDateTime fim = inicioDoDia(dia.plusDays(1));

        // Fechamentos da tendência (últimos dias) e do próprio dia, com os pedidos dessas comandas em uma consulta.
        List<Comanda> fechadasNaTendencia = comandaRepository.findFechadasNoPeriodo(StatusComandaEnum.FECHADA.getValue(),
                inicioDoDia(dia.minusDays(DIAS_TENDENCIA - 1L)), fim);
        Map<Long, BigDecimal> valorPorComanda = valorPorComanda(fechadasNaTendencia);
        List<Comanda> fechadasNoDia = fechadasNaTendencia.stream()
                .filter(comanda -> !comanda.getDataHoraSaida().isBefore(inicio))
                .toList();

        List<Pedido> pedidosDoDia = pedidoRepository.findDoPeriodo(inicio, fim);
        List<Pedido> vendidos = pedidosDoDia.stream().filter(RelatorioService::cobrado).toList();
        // Contagens de pedidos (cozinha): sem as inscrições em eventos e as taxas de pacote, que só entram nos valores.
        List<Pedido> pedidosDaCozinha = pedidosDoDia.stream().filter(p -> !p.itemAvulso()).toList();
        List<Comanda> entradas = comandaRepository.findEntradasNoPeriodo(inicio, fim);

        BigDecimal arrecadado = soma(fechadasNoDia.stream().map(c -> valorPorComanda.get(c.getId())));
        return new RelatorioDiarioDTO(
                dia,
                arrecadado,
                soma(vendidos.stream().map(RelatorioService::valor)),
                fechadasNoDia.isEmpty() ? BigDecimal.ZERO.setScale(2)
                        : arrecadado.divide(BigDecimal.valueOf(fechadasNoDia.size()), 2, RoundingMode.HALF_UP),
                entradas.stream().filter(c -> StatusComandaEnum.ABERTA.getValue().equals(c.getStatus())).count(),
                fechadasNoDia.size(),
                entradas.size(),
                permanenciaMedia(fechadasNoDia),
                pedidosDaCozinha.size(),
                pedidosDaCozinha.stream().filter(p -> p.getStatus() == StatusPedidoEnum.FINALIZADO).count(),
                pedidosDaCozinha.stream().filter(p -> p.getStatus() == StatusPedidoEnum.CANCELADO).count(),
                vendidos.stream().mapToLong(RelatorioService::quantidade).sum(),
                pedidosPorStatus(pedidosDaCozinha),
                produtos(vendidos),
                categorias(vendidos),
                formasPagamento(fechadasNoDia, valorPorComanda),
                porHora(vendidos, fechadasNoDia, valorPorComanda),
                tendencia(dia, fechadasNaTendencia, valorPorComanda));
    }

    private LocalDateTime inicioDoDia(LocalDate dia) {
        return dia.atStartOfDay(fusoEstabelecimento).withZoneSameInstant(fusoDados).toLocalDateTime();
    }

    /** Converte um horário gravado (fuso da JVM) para o fuso do estabelecimento. */
    private LocalDateTime noEstabelecimento(LocalDateTime horario) {
        return horario.atZone(fusoDados).withZoneSameInstant(fusoEstabelecimento).toLocalDateTime();
    }

    private Map<Long, BigDecimal> valorPorComanda(List<Comanda> comandas) {
        Map<Long, BigDecimal> valores = new LinkedHashMap<>();
        comandas.forEach(comanda -> valores.put(comanda.getId(), BigDecimal.ZERO.setScale(2)));
        if (comandas.isEmpty()) {
            return valores;
        }
        pedidoRepository.findByComandaIn(comandas).stream()
                .filter(RelatorioService::cobrado)
                .filter(pedido -> pedido.getComanda() != null)
                .forEach(pedido -> valores.merge(pedido.getComanda().getId(), valor(pedido), BigDecimal::add));
        return valores;
    }

    private Long permanenciaMedia(List<Comanda> fechadas) {
        List<Long> minutos = fechadas.stream()
                .filter(c -> c.getDataHoraEntrada() != null && c.getDataHoraSaida() != null)
                .map(c -> Duration.between(c.getDataHoraEntrada(), c.getDataHoraSaida()).toMinutes())
                .filter(duracao -> duracao >= 0)
                .toList();
        return minutos.isEmpty() ? null
                : Math.round(minutos.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private static List<Contagem> pedidosPorStatus(List<Pedido> pedidos) {
        Map<StatusPedidoEnum, Long> porStatus = pedidos.stream()
                .filter(p -> p.getStatus() != null)
                .collect(Collectors.groupingBy(Pedido::getStatus, Collectors.counting()));
        return Arrays.stream(StatusPedidoEnum.values())
                .map(status -> new Contagem(status.name(), status.getValue(), porStatus.getOrDefault(status, 0L)))
                .toList();
    }

    private static List<ProdutoVendido> produtos(List<Pedido> vendidos) {
        Map<Long, List<Pedido>> porProduto = vendidos.stream()
                .filter(p -> p.getProduto() != null)
                .collect(Collectors.groupingBy(p -> p.getProduto().getId(), LinkedHashMap::new, Collectors.toList()));
        return porProduto.values().stream()
                .map(pedidos -> {
                    Produto produto = pedidos.get(0).getProduto();
                    return new ProdutoVendido(produto.getId(), produto.getNome(), rotulo(produto.getCategoria()),
                            pedidos.stream().mapToLong(RelatorioService::quantidade).sum(),
                            soma(pedidos.stream().map(RelatorioService::valor)));
                })
                .sorted(Comparator.comparingLong(ProdutoVendido::quantidade).reversed()
                        .thenComparing(ProdutoVendido::valor, Comparator.reverseOrder()))
                .toList();
    }

    private static List<Agrupamento> categorias(List<Pedido> vendidos) {
        Map<CategoriaProdutoEnum, List<Pedido>> porCategoria = vendidos.stream()
                .filter(p -> p.getProduto() != null)
                .collect(Collectors.groupingBy(p -> Objects.requireNonNullElse(p.getProduto().getCategoria(),
                        CategoriaProdutoEnum.OUTROS)));
        return Arrays.stream(CategoriaProdutoEnum.values())
                .filter(porCategoria::containsKey)
                .map(categoria -> {
                    List<Pedido> pedidos = porCategoria.get(categoria);
                    return new Agrupamento(categoria.name(), categoria.getValue(),
                            pedidos.stream().mapToLong(RelatorioService::quantidade).sum(),
                            soma(pedidos.stream().map(RelatorioService::valor)));
                })
                .sorted(Comparator.comparing(Agrupamento::valor).reversed())
                .toList();
    }

    private static List<Agrupamento> formasPagamento(List<Comanda> fechadas, Map<Long, BigDecimal> valorPorComanda) {
        Map<String, List<Comanda>> porForma = fechadas.stream().collect(Collectors.groupingBy(
                c -> c.getFormaPagamento() == null ? SEM_FORMA_PAGAMENTO : c.getFormaPagamento().name()));
        List<Agrupamento> formas = new ArrayList<>();
        for (FormaPagamentoEnum forma : FormaPagamentoEnum.values()) {
            List<Comanda> comandas = porForma.getOrDefault(forma.name(), List.of());
            formas.add(new Agrupamento(forma.name(), forma.getValue(), comandas.size(),
                    soma(comandas.stream().map(c -> valorPorComanda.get(c.getId())))));
        }
        // Comandas fechadas antes da forma de pagamento existir.
        List<Comanda> semForma = porForma.getOrDefault(SEM_FORMA_PAGAMENTO, List.of());
        if (!semForma.isEmpty()) {
            formas.add(new Agrupamento(SEM_FORMA_PAGAMENTO, "Não informada", semForma.size(),
                    soma(semForma.stream().map(c -> valorPorComanda.get(c.getId())))));
        }
        return formas;
    }

    private List<Hora> porHora(List<Pedido> vendidos, List<Comanda> fechadas, Map<Long, BigDecimal> valorPorComanda) {
        long[] pedidos = new long[24];
        BigDecimal[] vendido = new BigDecimal[24];
        BigDecimal[] arrecadado = new BigDecimal[24];
        Arrays.fill(vendido, BigDecimal.ZERO);
        Arrays.fill(arrecadado, BigDecimal.ZERO);
        for (Pedido pedido : vendidos) {
            if (pedido.getDataHora() != null) {
                int hora = noEstabelecimento(pedido.getDataHora()).getHour();
                pedidos[hora]++;
                vendido[hora] = vendido[hora].add(valor(pedido));
            }
        }
        for (Comanda comanda : fechadas) {
            int hora = noEstabelecimento(comanda.getDataHoraSaida()).getHour();
            arrecadado[hora] = arrecadado[hora].add(valorPorComanda.getOrDefault(comanda.getId(), BigDecimal.ZERO));
        }
        List<Hora> horas = new ArrayList<>(24);
        for (int hora = 0; hora < 24; hora++) {
            horas.add(new Hora(hora, pedidos[hora], escala(vendido[hora]), escala(arrecadado[hora])));
        }
        return horas;
    }

    private List<Dia> tendencia(LocalDate dia, List<Comanda> fechadas, Map<Long, BigDecimal> valorPorComanda) {
        Map<LocalDate, List<Comanda>> porDia = fechadas.stream()
                .collect(Collectors.groupingBy(c -> noEstabelecimento(c.getDataHoraSaida()).toLocalDate()));
        List<Dia> dias = new ArrayList<>(DIAS_TENDENCIA);
        for (int i = DIAS_TENDENCIA - 1; i >= 0; i--) {
            LocalDate data = dia.minusDays(i);
            List<Comanda> doDia = porDia.getOrDefault(data, List.of());
            dias.add(new Dia(data, soma(doDia.stream().map(c -> valorPorComanda.get(c.getId()))), doDia.size()));
        }
        return dias;
    }

    private static boolean cobrado(Pedido pedido) {
        return pedido.getStatus() != StatusPedidoEnum.CANCELADO;
    }

    private static long quantidade(Pedido pedido) {
        return pedido.getQuantidade() == null ? 0 : pedido.getQuantidade();
    }

    /** Valor com o preço gravado no pedido: os relatórios de dias anteriores não mudam quando o preço muda. */
    private static BigDecimal valor(Pedido pedido) {
        return pedido.valorTotal();
    }

    private static BigDecimal soma(java.util.stream.Stream<BigDecimal> valores) {
        return escala(valores.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String rotulo(CategoriaProdutoEnum categoria) {
        return (categoria == null ? CategoriaProdutoEnum.OUTROS : categoria).getValue();
    }
}
