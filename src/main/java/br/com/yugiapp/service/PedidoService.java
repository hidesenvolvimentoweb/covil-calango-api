package br.com.yugiapp.service;

import br.com.yugiapp.converter.PedidoConverter;
import br.com.yugiapp.dto.PedidoFilterRequestDTO;
import br.com.yugiapp.dto.PedidoRequestDTO;
import br.com.yugiapp.dto.PedidoResponseDTO;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.model.Produto;
import br.com.yugiapp.repository.PagamentoRepository;
import br.com.yugiapp.repository.PedidoRepository;
import br.com.yugiapp.repository.ProdutoRepository;
import br.com.yugiapp.specs.PedidoSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PedidoService {

    static final long QUANTIDADE_MAXIMA = Produto.QUANTIDADE_MAXIMA_POR_PEDIDO;
    /** Tamanho da coluna OBSERVACAO (VARCHAR(255)). */
    static final int TAMANHO_MAXIMO_OBSERVACAO = 255;
    private static final Map<StatusPedidoEnum, Set<StatusPedidoEnum>> TRANSICOES_PERMITIDAS = Map.of(
            StatusPedidoEnum.SOLICITADO, Set.of(StatusPedidoEnum.EM_ANDAMENTO, StatusPedidoEnum.CANCELADO),
            StatusPedidoEnum.EM_ANDAMENTO, Set.of(StatusPedidoEnum.PRONTO, StatusPedidoEnum.CANCELADO),
            StatusPedidoEnum.PRONTO, Set.of(StatusPedidoEnum.FINALIZADO));
    /** Pedidos que ainda não saíram da cozinha: ao cancelar ou excluir, as unidades voltam ao estoque. */
    private static final Set<StatusPedidoEnum> STATUS_QUE_DEVOLVEM_ESTOQUE =
            Set.of(StatusPedidoEnum.SOLICITADO, StatusPedidoEnum.EM_ANDAMENTO);

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ComandaService comandaService;
    private final PedidoSpecifications pedidoSpecifications;
    private final PedidoConverter pedidoConverter;
    private final PagamentoRepository pagamentoRepository;

    public Page<Pedido> getAll(Pageable pageable) {
        return pedidoRepository.findAll(pageable);
    }

    public List<Pedido> getAllByFilters(PedidoFilterRequestDTO pedidoFilterRequestDTO) {
        return pedidoRepository.findAll(pedidoSpecifications.getSpecs(pedidoFilterRequestDTO));
    }

    public List<PedidoResponseDTO> getAllResponseDTOByFilters(PedidoFilterRequestDTO pedidoFilterRequestDTO) {
        return pedidoConverter.toResponseDTO(pedidoRepository.findAll(pedidoSpecifications.getSpecs(pedidoFilterRequestDTO)));
    }

    public List<Pedido> getAll() {
        return pedidoRepository.findAllPendentes();
    }

    public Pedido getById(Long id) {
        return pedidoRepository.findById(id).orElseThrow();
    }

    /** Exclui o pedido e o devolve (já removido), para quem chamou saber de qual comanda ele era. */
    @Transactional
    public Pedido delete(Long id) {
        Pedido pedido = pedidoRepository.findByIdParaAtualizacao(id).orElseThrow();
        boolean devolverEstoque = STATUS_QUE_DEVOLVEM_ESTOQUE.contains(pedido.getStatus());
        pedidoRepository.delete(pedido);
        if (devolverEstoque) {
            devolverEstoque(pedido);
        }
        return pedido;
    }

    /**
     * Registra o pedido e dá baixa no estoque do produto na mesma transação: se a gravação falhar, a baixa é desfeita.
     */
    @Transactional
    public Pedido save(PedidoRequestDTO pedidoRequestDTO) {
        validar(pedidoRequestDTO);
        Comanda comanda = comandaService.getByNumero(pedidoRequestDTO.getComanda());
        if (StatusComandaEnum.FECHADA.getValue().equals(comanda.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sua comanda foi finalizada. Gere uma nova comanda para fazer pedidos");
        }
        // O valor do PIX já foi fixado: um pedido novo ficaria fora da cobrança.
        if (pagamentoRepository.existsByComandaNumeroAndStatus(comanda.getNumero(), StatusPagamentoEnum.PENDENTE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Há um pagamento PIX em andamento para esta comanda. Cancele o PIX para fazer novos pedidos");
        }
        Produto produto = produtoRepository.findById(pedidoRequestDTO.getProduto().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
        baixarEstoque(produto, pedidoRequestDTO.getQuantidade());
        String observacao = pedidoRequestDTO.getObservacao() == null || pedidoRequestDTO.getObservacao().isBlank()
                ? null
                : pedidoRequestDTO.getObservacao().trim();
        return pedidoRepository.save(Pedido.builder()
                .dataHora(LocalDateTime.now())
                .status(StatusPedidoEnum.SOLICITADO)
                .produto(produto)
                .observacao(observacao)
                .quantidade(pedidoRequestDTO.getQuantidade())
                .comanda(comanda)
                .build());
    }

    /**
     * Altera o status seguindo o fluxo da cozinha: Solicitado → Em Andamento → Pronto → Finalizado, com
     * cancelamento antes de ficar pronto. Pedidos finalizados ou cancelados não mudam mais.
     */
    @Transactional
    public Pedido alterarStatus(Long id, StatusPedidoEnum status) {
        Pedido pedido = pedidoRepository.findByIdParaAtualizacao(id).orElseThrow();
        if (!TRANSICOES_PERMITIDAS.getOrDefault(pedido.getStatus(), Set.of()).contains(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível alterar um pedido "
                    + descricao(pedido.getStatus()) + " para " + status.getValue());
        }
        pedido.setStatus(status);
        Pedido atualizado = pedidoRepository.save(pedido);
        if (status == StatusPedidoEnum.CANCELADO) {
            devolverEstoque(atualizado);
        }
        return atualizado;
    }

    /**
     * Baixa atômica (ver ProdutoRepository.baixarEstoque). A mensagem não informa quantas unidades restam: o cliente
     * só percebe o limite que o app aplica à quantidade.
     */
    private void baixarEstoque(Produto produto, long quantidade) {
        if (produto.getEstoque() == null) {
            return;
        }
        if (produtoRepository.baixarEstoque(produto.getId(), Math.toIntExact(quantidade)) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, produto.getEstoque() == 0
                    ? produto.getNome() + " esgotou. Escolha outro produto"
                    : "Não há unidades suficientes de " + produto.getNome() + " no momento. Diminua a quantidade do pedido");
        }
    }

    private void devolverEstoque(Pedido pedido) {
        if (pedido.getProduto() != null && pedido.getQuantidade() != null) {
            produtoRepository.devolverEstoque(pedido.getProduto().getId(), Math.toIntExact(pedido.getQuantidade()));
        }
    }

    private static void validar(PedidoRequestDTO pedido) {
        if (pedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o pedido");
        }
        if (pedido.getComanda() == null || pedido.getComanda().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a comanda do pedido");
        }
        if (pedido.getProduto() == null || pedido.getProduto().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o produto do pedido");
        }
        Long quantidade = pedido.getQuantidade();
        if (quantidade == null || quantidade < 1 || quantidade > QUANTIDADE_MAXIMA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A quantidade deve ser entre 1 e " + QUANTIDADE_MAXIMA);
        }
        if (pedido.getObservacao() != null && pedido.getObservacao().trim().length() > TAMANHO_MAXIMO_OBSERVACAO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A observação deve ter no máximo " + TAMANHO_MAXIMO_OBSERVACAO + " caracteres");
        }
    }

    private static String descricao(StatusPedidoEnum status) {
        return status == null ? "sem status" : "com status " + status.getValue();
    }
}
