package br.com.yugiapp.service;

import br.com.yugiapp.dto.PacoteConfiguracaoResponseDTO;
import br.com.yugiapp.dto.PacoteResponseDTO;
import br.com.yugiapp.enums.CategoriaPacoteEnum;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPacoteEnum;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.enums.StatusPedidoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pacote;
import br.com.yugiapp.model.Parametro;
import br.com.yugiapp.model.Pedido;
import br.com.yugiapp.repository.PacoteRepository;
import br.com.yugiapp.repository.PagamentoRepository;
import br.com.yugiapp.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Pacotes deixados na loja pelo app para outra pessoa retirar. Quem deixa precisa estar com a comanda aberta: a taxa
 * do pacote (parâmetro PACOTE_VALOR) entra nela como o pedido "Pacote CODIGO". A retirada é feita pela equipe no
 * web, com o código informado por quem recebe e a foto do item entregue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PacoteService {

    static final String PARAMETRO_VALOR = "PACOTE_VALOR";
    static final BigDecimal VALOR_PADRAO = new BigDecimal("0.50");
    static final String PREFIXO_PEDIDO = "Pacote ";
    static final int TAMANHO_CODIGO = 6;
    /** Sem 0/O e 1/I/L: o código é ditado ao atendente. */
    private static final char[] CARACTERES_CODIGO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int TAMANHO_MAXIMO_TEXTO = 255;
    /** Limite da listagem da equipe (os aguardando retirada são poucos; o histórico fica nos mais recentes). */
    private static final int LIMITE_LISTAGEM = 300;

    private final PacoteRepository pacoteRepository;
    private final ComandaService comandaService;
    private final PedidoRepository pedidoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final ParametroService parametroService;
    private final ArquivoService arquivoService;
    private final SecureRandom random = new SecureRandom();

    public PacoteConfiguracaoResponseDTO getConfiguracao() {
        return PacoteConfiguracaoResponseDTO.builder()
                .valorTaxa(getValorTaxa().doubleValue())
                .categorias(Arrays.stream(CategoriaPacoteEnum.values())
                        .map(categoria -> new PacoteConfiguracaoResponseDTO.Categoria(categoria.name(), categoria.getValue()))
                        .toList())
                .build();
    }

    /** Taxa por pacote; zero deixa o serviço gratuito. Valor inválido no parâmetro usa o padrão. */
    public BigDecimal getValorTaxa() {
        return parametroService.findByNome(PARAMETRO_VALOR)
                .map(Parametro::getDescricao)
                .filter(texto -> !texto.isBlank())
                .map(PacoteService::lerValor)
                .orElse(VALOR_PADRAO);
    }

    private static BigDecimal lerValor(String texto) {
        try {
            BigDecimal valor = new BigDecimal(texto.replace("R$", "").trim().replace(',', '.'));
            if (valor.signum() >= 0) {
                return valor;
            }
        } catch (NumberFormatException ignorado) {
            // registrado abaixo
        }
        log.warn("Valor inválido no parâmetro {}: '{}'. Usando o padrão {}", PARAMETRO_VALOR, texto, VALOR_PADRAO);
        return VALOR_PADRAO;
    }

    /** O cliente deixa um pacote: grava a foto, gera o código e lança a taxa na comanda. */
    @Transactional
    public Pacote depositar(String numeroComanda, CategoriaPacoteEnum categoria, String descricao, String destinatario,
                            MultipartFile foto) {
        if (categoria == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o que está sendo deixado");
        }
        String descricaoTratada = textoObrigatorio(descricao, "Descreva o que está sendo deixado", "A descrição");
        String destinatarioTratado = textoObrigatorio(destinatario, "Informe quem vai receber o pacote",
                "O nome de quem vai receber");
        if (foto == null || foto.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tire uma foto do que está sendo deixado");
        }
        // Bloqueia a comanda: o fechamento e a geração do PIX esperam a taxa entrar (e vice-versa).
        Comanda comanda = comandaAberta(numeroComanda);
        BigDecimal taxa = getValorTaxa();
        if (taxa.signum() > 0
                && pagamentoRepository.existsByComandaNumeroAndStatus(comanda.getNumero(), StatusPagamentoEnum.PENDENTE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Há um pagamento pelo app em andamento para esta comanda. Cancele o pagamento para deixar um pacote");
        }
        String codigo = gerarCodigo();
        String caminhoFoto = salvarFoto(foto);
        Pedido pedido = null;
        if (taxa.signum() > 0) {
            pedido = pedidoRepository.save(Pedido.builder()
                    .dataHora(LocalDateTime.now())
                    // Não passa pela cozinha: entra na comanda já finalizado.
                    .status(StatusPedidoEnum.FINALIZADO)
                    .descricao(PREFIXO_PEDIDO + codigo)
                    .quantidade(1L)
                    .valorUnitario(taxa.doubleValue())
                    .comanda(comanda)
                    .build());
        }
        return pacoteRepository.save(Pacote.builder()
                .codigo(codigo)
                .categoria(categoria)
                .descricao(descricaoTratada)
                .destinatario(destinatarioTratado)
                .status(StatusPacoteEnum.AGUARDANDO_RETIRADA)
                .comanda(comanda)
                .pedido(pedido)
                .fotoDeposito(caminhoFoto)
                .dataHoraDeposito(LocalDateTime.now())
                .build());
    }

    /** Pacotes deixados com a comanda de quem consulta (app). */
    @Transactional(readOnly = true)
    public List<PacoteResponseDTO> getDaComanda(String numeroComanda) {
        return pacoteRepository.findByComandaNumeroOrderByDataHoraDepositoDescIdDesc(numeroComanda).stream()
                .map(pacote -> toResponse(pacote, false))
                .toList();
    }

    /** O cliente desiste do pacote (pegou o item de volta): a taxa sai da comanda. */
    @Transactional
    public Pacote cancelarPeloCliente(Long id, String numeroComanda) {
        Pacote pacote = pacoteRepository.findByIdParaAtualizacao(id)
                .filter(encontrado -> encontrado.getComanda().getNumero().equals(numeroComanda))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pacote não encontrado"));
        exigirAguardandoRetirada(pacote);
        if (comandaFechada(comandaService.travarParaAtualizacao(numeroComanda))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sua comanda foi finalizada. Peça ao atendente para cancelar o pacote");
        }
        cancelar(pacote);
        return pacote;
    }

    /**
     * A equipe cancela (ou recusa) um pacote que ainda não foi retirado. Com a comanda de quem deixou aberta, a taxa
     * sai dela; com a comanda já fechada (e paga), a taxa continua no histórico.
     */
    @Transactional
    public Pacote cancelarPelaEquipe(Long id) {
        Pacote pacote = pacoteRepository.findByIdParaAtualizacao(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pacote não encontrado"));
        exigirAguardandoRetirada(pacote);
        if (comandaFechada(comandaService.travarParaAtualizacao(pacote.getComanda().getNumero()))) {
            pacote.setStatus(StatusPacoteEnum.CANCELADO);
            pacote.setDataHoraCancelamento(LocalDateTime.now());
            return pacoteRepository.save(pacote);
        }
        cancelar(pacote);
        return pacote;
    }

    @Transactional(readOnly = true)
    public List<PacoteResponseDTO> getAll(Collection<StatusPacoteEnum> status) {
        Collection<StatusPacoteEnum> filtro = status == null || status.isEmpty()
                ? List.of(StatusPacoteEnum.values()) : status;
        return pacoteRepository.findByStatusInOrderByDataHoraDepositoDescIdDesc(filtro, Pageable.ofSize(LIMITE_LISTAGEM))
                .stream()
                .map(pacote -> {
                    PacoteResponseDTO resposta = toResponse(pacote, true);
                    // Quem recebe precisa informar o código: a listagem não o mostra enquanto o pacote espera a retirada.
                    if (pacote.getStatus() == StatusPacoteEnum.AGUARDANDO_RETIRADA) {
                        resposta.setCodigo(null);
                    }
                    return resposta;
                })
                .toList();
    }

    /** Pacote do código informado por quem vai receber (tela de retirada do web). */
    @Transactional(readOnly = true)
    public PacoteResponseDTO getByCodigo(String codigo) {
        String normalizado = normalizarCodigo(codigo);
        if (normalizado.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o código do pacote");
        }
        return pacoteRepository.findByCodigo(normalizado)
                .map(pacote -> toResponse(pacote, true))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhum pacote com o código " + normalizado));
    }

    /** Baixa do pacote: o atendente confere o código, registra a foto do item entregue e o entrega. */
    @Transactional
    public Pacote retirar(Long id, String codigo, MultipartFile foto, String atendente) {
        Pacote pacote = pacoteRepository.findByIdParaAtualizacao(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pacote não encontrado"));
        if (!pacote.getCodigo().equals(normalizarCodigo(codigo))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O código não confere com o pacote");
        }
        exigirAguardandoRetirada(pacote);
        if (foto == null || foto.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tire uma foto do item sendo retirado");
        }
        pacote.setFotoRetirada(salvarFoto(foto));
        pacote.setStatus(StatusPacoteEnum.RETIRADO);
        pacote.setDataHoraRetirada(LocalDateTime.now());
        pacote.setRetiradoPor(atendente);
        return pacoteRepository.save(pacote);
    }

    public PacoteResponseDTO toResponse(Pacote pacote, boolean equipe) {
        return PacoteResponseDTO.builder()
                .id(pacote.getId())
                .codigo(pacote.getCodigo())
                .categoria(pacote.getCategoria())
                .descricao(pacote.getDescricao())
                .destinatario(pacote.getDestinatario())
                .status(pacote.getStatus())
                .remetente(pacote.getComanda().getNomeCliente())
                // O número da comanda dá acesso a ela no app: só a equipe o recebe.
                .comanda(equipe ? pacote.getComanda().getNumero() : null)
                .valorTaxa(pacote.getPedido() == null ? null : pacote.getPedido().getValorUnitario())
                .fotoDeposito(pacote.getFotoDeposito())
                .fotoRetirada(pacote.getFotoRetirada())
                .dataHoraDeposito(pacote.getDataHoraDeposito())
                .dataHoraRetirada(pacote.getDataHoraRetirada())
                .retiradoPor(equipe ? pacote.getRetiradoPor() : null)
                .dataHoraCancelamento(pacote.getDataHoraCancelamento())
                .build();
    }

    /** Cancela com a comanda aberta (já bloqueada): a taxa sai dela, se o PIX ainda não estiver em andamento. */
    private void cancelar(Pacote pacote) {
        Pedido pedido = pacote.getPedido();
        if (pedido != null) {
            if (pagamentoRepository.existsByComandaNumeroAndStatus(pacote.getComanda().getNumero(), StatusPagamentoEnum.PENDENTE)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Há um pagamento pelo app em andamento para a comanda "
                        + pacote.getComanda().getNumero() + ". Cancele o pagamento antes de cancelar o pacote");
            }
            pacote.setPedido(null);
        }
        pacote.setStatus(StatusPacoteEnum.CANCELADO);
        pacote.setDataHoraCancelamento(LocalDateTime.now());
        pacoteRepository.saveAndFlush(pacote);
        if (pedido != null) {
            pedidoRepository.delete(pedido);
            pacote.setTaxaRemovida(pedido);
        }
    }

    private Comanda comandaAberta(String numeroComanda) {
        if (numeroComanda == null || comandaService.findByNumero(numeroComanda).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Gere uma comanda para deixar um pacote");
        }
        Comanda comanda = comandaService.travarParaAtualizacao(numeroComanda);
        if (comandaFechada(comanda)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sua comanda foi finalizada. Gere uma nova comanda para deixar um pacote");
        }
        return comanda;
    }

    private static void exigirAguardandoRetirada(Pacote pacote) {
        if (pacote.getStatus() == StatusPacoteEnum.RETIRADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pacote já foi retirado");
        }
        if (pacote.getStatus() == StatusPacoteEnum.CANCELADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pacote foi cancelado");
        }
    }

    private static boolean comandaFechada(Comanda comanda) {
        return StatusComandaEnum.FECHADA.getValue().equals(comanda.getStatus());
    }

    private String gerarCodigo() {
        for (int tentativa = 0; tentativa < 20; tentativa++) {
            StringBuilder codigo = new StringBuilder(TAMANHO_CODIGO);
            for (int i = 0; i < TAMANHO_CODIGO; i++) {
                codigo.append(CARACTERES_CODIGO[random.nextInt(CARACTERES_CODIGO.length)]);
            }
            if (!pacoteRepository.existsByCodigo(codigo.toString())) {
                return codigo.toString();
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível gerar o código do pacote. Tente novamente");
    }

    /** Código digitado pelo atendente: sem espaços e hífens, em maiúsculas. */
    static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /** Grava a foto e a apaga do disco se a transação não for concluída (o pacote não fica gravado). */
    private String salvarFoto(MultipartFile foto) {
        String caminho = arquivoService.salvarImagem(foto, ArquivoService.PASTA_PACOTES);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        arquivoService.excluir(caminho);
                    }
                }
            });
        }
        return caminho;
    }

    private static String textoObrigatorio(String valor, String mensagemVazio, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagemVazio);
        }
        String texto = valor.trim();
        if (texto.length() > TAMANHO_MAXIMO_TEXTO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    campo + " deve ter no máximo " + TAMANHO_MAXIMO_TEXTO + " caracteres");
        }
        return texto;
    }
}
