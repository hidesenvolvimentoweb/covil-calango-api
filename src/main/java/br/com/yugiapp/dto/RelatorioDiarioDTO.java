package br.com.yugiapp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Relatório do dia para o Dashboard (RelatorioService). Valores em reais com 2 casas.
 *
 * @param arrecadado          soma das comandas fechadas no dia (o que entrou no caixa)
 * @param vendido             soma dos pedidos feitos no dia, exceto os cancelados (inclui comandas ainda abertas)
 * @param ticketMedio         arrecadado ÷ comandas fechadas
 * @param permanenciaMediaMinutos tempo médio entre a entrada e a saída das comandas fechadas; nulo sem fechamentos
 * @param produtos            produtos que saíram no dia (pedidos não cancelados), do mais vendido ao menos vendido
 * @param porHora             as 24 horas do dia, no fuso do estabelecimento
 * @param tendencia           arrecadado nos últimos dias, terminando no dia do relatório
 */
public record RelatorioDiarioDTO(
        LocalDate data,
        BigDecimal arrecadado,
        BigDecimal vendido,
        BigDecimal ticketMedio,
        long comandasAbertas,
        long comandasFechadas,
        long comandasAtendidas,
        Long permanenciaMediaMinutos,
        long pedidosRegistrados,
        long pedidosEntregues,
        long pedidosCancelados,
        long itensVendidos,
        List<Contagem> pedidosPorStatus,
        List<ProdutoVendido> produtos,
        List<Agrupamento> categorias,
        List<Agrupamento> formasPagamento,
        List<Hora> porHora,
        List<Dia> tendencia) {

    public record Contagem(String chave, String rotulo, long quantidade) {
    }

    public record ProdutoVendido(Long id, String nome, String categoria, long quantidade, BigDecimal valor) {
    }

    /** Ex.: categoria de produto (quantidade de itens) ou forma de pagamento (quantidade de comandas). */
    public record Agrupamento(String chave, String rotulo, long quantidade, BigDecimal valor) {
    }

    /** @param arrecadado comandas fechadas nesta hora; @param vendido pedidos feitos nesta hora */
    public record Hora(int hora, long pedidos, BigDecimal vendido, BigDecimal arrecadado) {
    }

    public record Dia(LocalDate data, BigDecimal arrecadado, long comandasFechadas) {
    }
}
