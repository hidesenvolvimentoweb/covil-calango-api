package br.com.yugiapp.service;

import br.com.yugiapp.dto.PagamentoCartaoRequestDTO;
import br.com.yugiapp.dto.PagamentoConfiguracaoDTO;
import br.com.yugiapp.dto.PagamentoResponseDTO;
import br.com.yugiapp.enums.MetodoPagamentoEnum;
import br.com.yugiapp.enums.FormaPagamentoEnum;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusPagamentoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Pagamento;
import br.com.yugiapp.repository.ComandaRepository;
import br.com.yugiapp.repository.PagamentoRepository;
import br.com.yugiapp.service.pagamento.CobrancaPix;
import br.com.yugiapp.service.pagamento.GatewayPagamento;
import br.com.yugiapp.service.pagamento.GatewayPagamentoException;
import br.com.yugiapp.service.pagamento.MercadoPagoGateway;
import br.com.yugiapp.service.pagamento.MotivoRecusaCartao;
import br.com.yugiapp.service.pagamento.PagamentoAtualizadoEvent;
import br.com.yugiapp.service.pagamento.PreferenciaCheckout;
import br.com.yugiapp.service.pagamento.SituacaoPagamento;
import br.com.yugiapp.service.pagamento.SolicitacaoCobrancaPix;
import br.com.yugiapp.service.pagamento.SolicitacaoPagamentoCartao;
import br.com.yugiapp.service.pagamento.SolicitacaoPreferencia;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pagamento da comanda pelo app: PIX, cartão de crédito ou débito à vista (formulário do Mercado Pago no app) e conta do
 * Mercado Pago (Checkout Pro, com o cartão salvo na conta do cliente).
 * <ul>
 *     <li>O valor é sempre calculado no servidor; o app nunca informa quanto vai pagar.</li>
 *     <li>O cartão chega como token: o número, a validade e o código de segurança nunca passam pela API.</li>
 *     <li>A confirmação vem do provedor: o webhook e a reconciliação periódica apenas disparam uma consulta ao
 *     provedor, cujo resultado é a fonte da verdade.</li>
 *     <li>Com o pagamento aprovado a comanda é fechada e o app é avisado em tempo real.</li>
 *     <li>Enquanto houver um pagamento pendente, novos pedidos ficam bloqueados (ver PedidoService), para o valor
 *     cobrado não ficar diferente do consumido.</li>
 * </ul>
 * Ordem dos bloqueios, para evitar deadlock entre webhook, reconciliação e cobrança: comanda, depois pagamento.
 */
@Slf4j
@Service
public class PagamentoService {

    /** Um PIX pendente é reaproveitado se ainda faltar pelo menos isso para expirar. */
    static final Duration MARGEM_REAPROVEITAMENTO = Duration.ofMinutes(5);
    /** Após a expiração, aguarda o provedor encerrar o PIX antes de encerrá-lo por conta própria. */
    static final Duration TOLERANCIA_EXPIRACAO = Duration.ofMinutes(5);
    /** Pagamentos recém-criados ficam fora da reconciliação (o webhook costuma chegar antes). */
    static final Duration IDADE_MINIMA_RECONCILIACAO = Duration.ofMinutes(1);
    /**
     * Tempo para concluir um pagamento com cartão que ficou pendente (verificação do banco aberta ou resposta do
     * provedor perdida). Depois disso ele é cancelado e os pedidos voltam a ser liberados.
     */
    static final Duration EXPIRACAO_CARTAO = Duration.ofMinutes(15);

    private static final Set<String> TIPOS_DOCUMENTO = Set.of("CPF", "CNPJ");
    private static final Pattern EMAIL_VALIDO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern BANDEIRA_VALIDA = Pattern.compile("[a-z0-9_]{2,40}");
    private static final Pattern EMISSOR_VALIDO = Pattern.compile("[0-9]{1,20}");
    private static final int TAMANHO_MAXIMO_TOKEN = 255;

    private final PagamentoRepository pagamentoRepository;
    private final ComandaRepository comandaRepository;
    private final ComandaService comandaService;
    private final GatewayPagamento gateway;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Duration expiracaoPix;
    private final String emailPagador;

    /** Chave pública do Mercado Pago, usada pelo formulário de cartão do app. Sem ela, o cartão fica indisponível. */
    @Value("${app.mercadopago.public-key:}")
    private String publicKey;

    public PagamentoService(PagamentoRepository pagamentoRepository,
                            ComandaRepository comandaRepository,
                            ComandaService comandaService,
                            GatewayPagamento gateway,
                            ApplicationEventPublisher eventPublisher,
                            PlatformTransactionManager transactionManager,
                            @Value("${app.pagamento.pix.expiracao-minutos:30}") long expiracaoMinutos,
                            @Value("${app.pagamento.email-pagador:}") String emailPagador) {
        this.pagamentoRepository = pagamentoRepository;
        this.comandaRepository = comandaRepository;
        this.comandaService = comandaService;
        this.gateway = gateway;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.expiracaoPix = Duration.ofMinutes(expiracaoMinutos);
        this.emailPagador = emailPagador;
    }

    void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    /** Formas de pagamento disponíveis no app. */
    public PagamentoConfiguracaoDTO getConfiguracao() {
        boolean configurado = gateway.isConfigurado();
        String chave = MercadoPagoGateway.normalizarToken(publicKey);
        boolean cartao = configurado && !chave.isEmpty();
        return new PagamentoConfiguracaoDTO(configurado, cartao, configurado, cartao ? chave : null);
    }

    /**
     * Gera o PIX da comanda, ou devolve o PIX pendente se ele ainda vale para o valor atual.
     * Se a comanda já foi paga nesse meio-tempo, devolve o pagamento aprovado.
     */
    @Transactional
    public PagamentoResponseDTO gerarPix(String numero) {
        exigirGateway("O pagamento via PIX está indisponível no momento");
        Comanda comanda = travarComandaParaPagar(numero);
        BigDecimal valor = valorAPagar(comanda);

        try {
            Optional<Pagamento> pendente = pendenteDa(comanda);
            if (pendente.isPresent()) {
                Pagamento atual = pendente.get();
                if (reaproveitavel(atual, MetodoPagamentoEnum.PIX, valor)) {
                    return PagamentoResponseDTO.of(atual);
                }
                encerrarPendente(atual);
                if (atual.getStatus() == StatusPagamentoEnum.PAGO) {
                    return PagamentoResponseDTO.of(atual);
                }
            }
            return PagamentoResponseDTO.of(criarPix(comanda, valor));
        } catch (GatewayPagamentoException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível gerar o PIX agora. Tente novamente em instantes", exception);
        }
    }

    /**
     * Cobra a comanda à vista no cartão de crédito ou débito. A resposta já traz o resultado:
     * <ul>
     *     <li>PAGO: a comanda é fechada;</li>
     *     <li>RECUSADO: a comanda continua aberta e a mensagem explica o motivo;</li>
     *     <li>PENDENTE: em análise, aguardando a verificação do banco (a resposta traz o desafio) ou com a resposta do
     *     provedor perdida. O webhook e a reconciliação concluem o pagamento.</li>
     * </ul>
     * Um pagamento pendente anterior (PIX, cartão ou Mercado Pago) é cancelado antes da nova cobrança.
     */
    @Transactional
    public PagamentoResponseDTO pagarComCartao(String numero, PagamentoCartaoRequestDTO request) {
        if (!getConfiguracao().cartao()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O pagamento com cartão está indisponível no momento");
        }
        PagamentoCartaoRequestDTO cartao = validarCartao(request);
        Comanda comanda = travarComandaParaPagar(numero);
        BigDecimal valor = valorAPagar(comanda);

        Optional<Pagamento> aprovado = encerrarPendenteAntesDeCobrar(comanda);
        if (aprovado.isPresent()) {
            return PagamentoResponseDTO.of(aprovado.get());
        }
        Pagamento pagamento = novoPagamento(comanda, MetodoPagamentoEnum.CARTAO_CREDITO, valor,
                LocalDateTime.now().plus(EXPIRACAO_CARTAO));
        SituacaoPagamento situacao;
        try {
            situacao = gateway.pagarComCartao(new SolicitacaoPagamentoCartao(
                    pagamento.getReferencia(), valor, descricao(comanda), cartao.getToken(), cartao.getBandeira(),
                    cartao.getEmissorId(), cartao.getEmail(), cartao.getTipoDocumento(), cartao.getDocumento(),
                    pagamento.getIdempotencyKey()));
        } catch (GatewayPagamentoException exception) {
            if (exception.isRecusa()) {
                // Recusado antes de cobrar (ex.: token inválido ou expirado): nada foi cobrado.
                pagamento.setMotivoRecusa(MotivoRecusaCartao.DADOS_INVALIDOS);
                atualizarStatus(pagamento, StatusPagamentoEnum.RECUSADO, false);
                return PagamentoResponseDTO.of(pagamento);
            }
            // Falha de comunicação: a cobrança pode ter sido feita. O pagamento fica pendente e a reconciliação o
            // procura no provedor pela referência (a chave de idempotência impede cobrar duas vezes).
            log.warn("Sem resposta do provedor ao cobrar o cartão do pagamento {}: {}", pagamento.getId(),
                    exception.getMessage());
            return PagamentoResponseDTO.of(pagamento);
        }
        vincular(pagamento, situacao);
        log.info("Cartão cobrado no pagamento {} da comanda {} (R$ {}): {} {}", pagamento.getId(), comanda.getNumero(),
                valor, situacao.statusGateway(), situacao.statusDetalhe());
        if (pagamento.getStatus() == StatusPagamentoEnum.PENDENTE) {
            pagamento.setDesafioUrl(situacao.desafioUrl());
            pagamento.setDesafioCreq(situacao.desafioCreq());
        }
        return PagamentoResponseDTO.of(pagamento);
    }

    /**
     * Gera o link do Checkout Pro para o cliente pagar na própria conta do Mercado Pago (cartão salvo lá), ou
     * devolve o link pendente se ele ainda vale para o valor atual.
     */
    @Transactional
    public PagamentoResponseDTO gerarPagamentoMercadoPago(String numero) {
        exigirGateway("O pagamento pelo Mercado Pago está indisponível no momento");
        Comanda comanda = travarComandaParaPagar(numero);
        BigDecimal valor = valorAPagar(comanda);

        try {
            Optional<Pagamento> pendente = pendenteDa(comanda);
            if (pendente.isPresent() && reaproveitavel(pendente.get(), MetodoPagamentoEnum.CARTEIRA_MERCADO_PAGO, valor)) {
                return PagamentoResponseDTO.of(pendente.get());
            }
            Optional<Pagamento> aprovado = encerrarPendenteAntesDeCobrar(comanda);
            if (aprovado.isPresent()) {
                return PagamentoResponseDTO.of(aprovado.get());
            }
            LocalDateTime expiracao = LocalDateTime.now().plus(expiracaoPix);
            Pagamento pagamento = novoPagamento(comanda, MetodoPagamentoEnum.CARTEIRA_MERCADO_PAGO, valor, expiracao);
            PreferenciaCheckout preferencia = gateway.criarPreferencia(new SolicitacaoPreferencia(
                    pagamento.getReferencia(), valor, descricao(comanda),
                    expiracao.atZone(ZoneId.systemDefault()).toOffsetDateTime(), pagamento.getIdempotencyKey(),
                    comanda.getNomeCliente(),
                    comanda.getUsuario() == null ? null : comanda.getUsuario().getEmail(),
                    comanda.getCelular()));
            pagamento.setPreferenciaId(preferencia.id());
            pagamento.setUrlCheckout(preferencia.url());
            log.info("Pagamento {} pelo Mercado Pago gerado para a comanda {} (R$ {}, preferência {})", pagamento.getId(),
                    comanda.getNumero(), valor, preferencia.id());
            return PagamentoResponseDTO.of(pagamentoRepository.save(pagamento));
        } catch (GatewayPagamentoException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível abrir o pagamento pelo Mercado Pago agora. Tente novamente em instantes", exception);
        }
    }

    /**
     * Estorna um pagamento aprovado pelo app (valor total, no provedor) e reabre a comanda, para a equipe fechá-la
     * com a forma de pagamento correta ou cancelar os pedidos. Somente o administrador estorna (JwtSecurityConfig).
     *
     * @return o número da comanda reaberta
     */
    @Transactional
    public String estornar(Long pagamentoId) {
        String numero = pagamentoRepository.findNumeroComandaById(pagamentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pagamento não encontrado"));
        Comanda comanda = travarComanda(numero);
        Pagamento pagamento = pagamentoRepository.findByIdParaAtualizacao(pagamentoId).orElseThrow();
        if (pagamento.getStatus() != StatusPagamentoEnum.PAGO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Só um pagamento aprovado pode ser estornado");
        }
        if (pagamento.getGatewayId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O pagamento não foi localizado no provedor");
        }
        try {
            gateway.estornar(pagamento.getGatewayId(), "estorno-" + pagamento.getIdempotencyKey());
        } catch (GatewayPagamentoException exception) {
            throw new ResponseStatusException(exception.isRecusa() ? HttpStatus.CONFLICT : HttpStatus.BAD_GATEWAY,
                    exception.isRecusa()
                            ? "O Mercado Pago recusou o estorno (o prazo pode ter passado ou o valor já foi devolvido)"
                            : "Não foi possível estornar agora. Tente novamente em instantes", exception);
        }
        comanda.setStatus(StatusComandaEnum.ABERTA.getValue());
        comanda.setFormaPagamento(null);
        comanda.setDataHoraSaida(null);
        comandaRepository.save(comanda);
        log.warn("Pagamento {} da comanda {} estornado pelo administrador; comanda reaberta", pagamento.getId(), numero);
        atualizarStatus(pagamento, StatusPagamentoEnum.ESTORNADO, false);
        return numero;
    }

    /** Último pagamento da comanda (o pendente, o aprovado ou a última tentativa). */
    @Transactional(readOnly = true)
    public Optional<PagamentoResponseDTO> consultarUltimo(String numero) {
        return pagamentoRepository.findFirstByComandaNumeroOrderByIdDesc(numero).map(PagamentoResponseDTO::of);
    }

    /**
     * Cancela o pagamento pendente da comanda (o cliente desistiu ou a equipe fechou a comanda de outra forma).
     * Se ele já tinha sido pago, o pagamento aprovado é registrado e devolvido.
     *
     * @return vazio quando não há pagamento pendente
     */
    @Transactional
    public Optional<PagamentoResponseDTO> cancelarPixPendente(String numero) {
        Comanda comanda = travarComanda(numero);
        Optional<Pagamento> pendente = pendenteDa(comanda);
        if (pendente.isEmpty()) {
            return Optional.empty();
        }
        try {
            encerrarPendente(pendente.get());
        } catch (GatewayPagamentoException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível cancelar o pagamento agora. Tente novamente em instantes", exception);
        }
        return pendente.map(PagamentoResponseDTO::of);
    }

    public boolean possuiPixPendente(String numero) {
        return pagamentoRepository.existsByComandaNumeroAndStatus(numero, StatusPagamentoEnum.PENDENTE);
    }

    /**
     * Trata a notificação do provedor sobre o pagamento informado, sempre consultando o provedor.
     * Um pagamento ainda sem id na API (Checkout Pro, ou cartão com a resposta perdida) é encontrado pela referência.
     * Falhas de comunicação são propagadas para o provedor reenviar a notificação.
     *
     * @return falso se o pagamento não é desta API
     */
    public boolean processarNotificacao(String gatewayId) {
        Optional<Long> id = pagamentoRepository.findIdByGatewayAndGatewayId(gateway.nome(), gatewayId);
        if (id.isPresent()) {
            transactionTemplate.executeWithoutResult(status -> sincronizar(id.get(), false));
            return true;
        }
        SituacaoPagamento situacao;
        try {
            situacao = gateway.consultar(gatewayId);
        } catch (GatewayPagamentoException exception) {
            if (!exception.isRecusa()) {
                throw exception;
            }
            // Ex.: id inexistente no provedor; reenviar a notificação não mudaria o resultado.
            log.info("Notificação de pagamento não encontrado no provedor ignorada ({} {})", gateway.nome(), gatewayId);
            return false;
        }
        Optional<Long> pelaReferencia = idDaReferencia(situacao.referencia());
        if (pelaReferencia.isEmpty()) {
            log.info("Notificação de pagamento desconhecido ignorada ({} {})", gateway.nome(), gatewayId);
            return false;
        }
        transactionTemplate.executeWithoutResult(status -> vincularPelaNotificacao(pelaReferencia.get(), situacao));
        return true;
    }

    /** Pagamentos pendentes a conferir no provedor (webhook perdido, PIX expirado). */
    public List<Long> pendentesParaReconciliar() {
        return pagamentoRepository.findIdsByStatusCriadosAntesDe(StatusPagamentoEnum.PENDENTE,
                LocalDateTime.now().minus(IDADE_MINIMA_RECONCILIACAO));
    }

    public void reconciliar(Long pagamentoId) {
        transactionTemplate.executeWithoutResult(status -> sincronizar(pagamentoId, true));
    }

    public boolean isGatewayConfigurado() {
        return gateway.isConfigurado();
    }

    private Pagamento criarPix(Comanda comanda, BigDecimal valor) {
        LocalDateTime expiracao = LocalDateTime.now().plus(expiracaoPix);
        Pagamento pagamento = novoPagamento(comanda, MetodoPagamentoEnum.PIX, valor, expiracao);

        CobrancaPix cobranca = gateway.criarCobrancaPix(new SolicitacaoCobrancaPix(
                pagamento.getReferencia(),
                valor,
                descricao(comanda),
                comanda.getNomeCliente(),
                emailPagador,
                expiracao.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                pagamento.getIdempotencyKey()));

        pagamento.setGatewayId(cobranca.gatewayId());
        pagamento.setStatusGateway(cobranca.statusGateway());
        pagamento.setPixCopiaECola(cobranca.copiaECola());
        pagamento.setPixQrCodeBase64(cobranca.qrCodeBase64());
        log.info("PIX {} gerado para a comanda {} (R$ {}, {} {})", pagamento.getId(), comanda.getNumero(), valor,
                gateway.nome(), cobranca.gatewayId());
        return pagamentoRepository.save(pagamento);
    }

    private Pagamento novoPagamento(Comanda comanda, MetodoPagamentoEnum metodo, BigDecimal valor, LocalDateTime expiracao) {
        LocalDateTime agora = LocalDateTime.now();
        return pagamentoRepository.saveAndFlush(Pagamento.builder()
                .comanda(comanda)
                .metodo(metodo)
                .status(StatusPagamentoEnum.PENDENTE)
                .valor(valor)
                .gateway(gateway.nome())
                .idempotencyKey(UUID.randomUUID().toString())
                .dataHoraCriacao(agora)
                .dataHoraExpiracao(expiracao)
                .dataHoraAtualizacao(agora)
                .build());
    }

    /** Consulta o provedor e aplica o resultado. Executa dentro de uma transação. */
    private void sincronizar(Long pagamentoId, boolean encerrarSeExpirado) {
        Optional<String> numero = pagamentoRepository.findNumeroComandaById(pagamentoId);
        if (numero.isEmpty()) {
            return;
        }
        travarComanda(numero.get());
        Pagamento pagamento = pagamentoRepository.findByIdParaAtualizacao(pagamentoId).orElseThrow();
        if (pagamento.getStatus() == StatusPagamentoEnum.ESTORNADO) {
            return;
        }
        if (pagamento.getGatewayId() == null) {
            // Checkout Pro ainda não pago, ou cartão com a resposta do provedor perdida: procura pela referência.
            if (pagamento.getStatus() == StatusPagamentoEnum.PENDENTE) {
                gateway.buscarPorReferencia(pagamento.getReferencia()).ifPresent(situacao -> vincular(pagamento, situacao));
            }
        } else {
            aplicar(pagamento, gateway.consultar(pagamento.getGatewayId()));
        }
        if (encerrarSeExpirado && pagamento.getStatus() == StatusPagamentoEnum.PENDENTE
                && LocalDateTime.now().isAfter(pagamento.getDataHoraExpiracao().plus(TOLERANCIA_EXPIRACAO))) {
            encerrarPendente(pagamento);
        }
    }

    /** Notificação de um pagamento que a API ainda não conhecia pelo id do provedor. */
    private void vincularPelaNotificacao(Long pagamentoId, SituacaoPagamento situacao) {
        Optional<String> numero = pagamentoRepository.findNumeroComandaById(pagamentoId);
        if (numero.isEmpty()) {
            return;
        }
        travarComanda(numero.get());
        Pagamento pagamento = pagamentoRepository.findByIdParaAtualizacao(pagamentoId).orElseThrow();
        if (pagamento.getStatus() == StatusPagamentoEnum.ESTORNADO || pagamento.getStatus() == StatusPagamentoEnum.PAGO) {
            return;
        }
        // Outra tentativa no mesmo link do Checkout Pro (ex.: o primeiro cartão foi recusado e o segundo aprovado).
        if (pagamento.getGatewayId() != null && !pagamento.getGatewayId().equals(situacao.gatewayId())
                && situacao.status() != StatusPagamentoEnum.PAGO) {
            return;
        }
        vincular(pagamento, situacao);
    }

    /**
     * Associa o pagamento do provedor ao da API e aplica a situação. No Checkout Pro, um cartão recusado não encerra o
     * pagamento: o cliente pode tentar outro cartão no mesmo link até ele expirar.
     */
    private void vincular(Pagamento pagamento, SituacaoPagamento situacao) {
        if (!pagamento.getReferencia().equals(situacao.referencia())) {
            log.error("Pagamento {} do provedor tem referência {} (esperada {}); ignorado", situacao.gatewayId(),
                    situacao.referencia(), pagamento.getReferencia());
            return;
        }
        if (pagamento.getMetodo() == MetodoPagamentoEnum.CARTEIRA_MERCADO_PAGO
                && situacao.status() == StatusPagamentoEnum.RECUSADO) {
            pagamento.setMotivoRecusa(limitar(situacao.statusDetalhe(), 80));
            pagamentoRepository.save(pagamento);
            return;
        }
        pagamento.setGatewayId(situacao.gatewayId());
        pagamento.setCartaoBandeira(limitar(situacao.bandeira(), 40));
        pagamento.setCartaoFinal(limitar(situacao.cartaoFinal(), 4));
        if (situacao.tipoCartao() != null) {
            pagamento.setCartaoTipo("debit_card".equals(situacao.tipoCartao())
                    ? FormaPagamentoEnum.DEBITO
                    : FormaPagamentoEnum.CREDITO);
        }
        pagamento.setStatusGateway(situacao.statusGateway());
        pagamentoRepository.save(pagamento);
        aplicar(pagamento, situacao);
    }

    /**
     * Cancela o pagamento pendente no provedor e confere o resultado: se ele foi pago antes do cancelamento, o
     * pagamento é aprovado; caso contrário fica cancelado/expirado.
     */
    private void encerrarPendente(Pagamento pagamento) {
        if (pagamento.getGatewayId() == null) {
            gateway.buscarPorReferencia(pagamento.getReferencia()).ifPresent(situacao -> vincular(pagamento, situacao));
        }
        if (pagamento.getStatus() == StatusPagamentoEnum.PENDENTE && pagamento.getGatewayId() != null) {
            try {
                gateway.cancelar(pagamento.getGatewayId());
            } catch (GatewayPagamentoException exception) {
                if (!exception.isRecusa()) {
                    throw exception;
                }
                // Recusa: o pagamento já não está pendente no provedor (pago ou expirado); a consulta mostra qual.
                log.info("Provedor recusou o cancelamento do pagamento {}: {}", pagamento.getId(), exception.getMessage());
            }
            aplicar(pagamento, gateway.consultar(pagamento.getGatewayId()));
        }
        if (pagamento.getStatus() == StatusPagamentoEnum.PENDENTE && pagamento.getPreferenciaId() != null) {
            try {
                gateway.expirarPreferencia(pagamento.getPreferenciaId());
            } catch (GatewayPagamentoException exception) {
                // O link expira sozinho no prazo; se for pago antes disso, a aprovação tardia é registrada.
                log.warn("Não foi possível encerrar o link de pagamento {}: {}", pagamento.getId(), exception.getMessage());
            }
        }
        if (pagamento.getStatus() == StatusPagamentoEnum.PENDENTE) {
            atualizarStatus(pagamento, statusDeEncerramento(pagamento), false);
        }
        // O próximo pagamento da comanda é inserido em seguida; o Hibernate executa inserts antes de updates,
        // e o índice único de pendente por comanda seria violado.
        pagamentoRepository.flush();
    }

    /** Antes de uma nova cobrança: encerra a pendente e devolve-a se ela tiver sido aprovada nesse meio-tempo. */
    private Optional<Pagamento> encerrarPendenteAntesDeCobrar(Comanda comanda) {
        Optional<Pagamento> pendente = pendenteDa(comanda);
        if (pendente.isEmpty()) {
            return Optional.empty();
        }
        try {
            encerrarPendente(pendente.get());
        } catch (GatewayPagamentoException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível cancelar o pagamento anterior agora. Tente novamente em instantes", exception);
        }
        return pendente.filter(pagamento -> pagamento.getStatus() == StatusPagamentoEnum.PAGO);
    }

    private void aplicar(Pagamento pagamento, SituacaoPagamento situacao) {
        if (!pagamento.getReferencia().equals(situacao.referencia())) {
            log.error("Pagamento {} do provedor tem referência {} (esperada {}); ignorado", situacao.gatewayId(),
                    situacao.referencia(), pagamento.getReferencia());
            return;
        }
        pagamento.setStatusGateway(situacao.statusGateway());
        StatusPagamentoEnum atual = pagamento.getStatus();
        StatusPagamentoEnum novo = situacao.status();
        if (novo == atual || novo == StatusPagamentoEnum.PENDENTE) {
            return;
        }

        switch (novo) {
            case PAGO -> {
                if (situacao.valor() == null || situacao.valor().compareTo(pagamento.getValor()) != 0) {
                    log.error("Pagamento {} aprovado com valor {} diferente do cobrado {}; comanda não fechada",
                            pagamento.getId(), situacao.valor(), pagamento.getValor());
                    return;
                }
                if (atual != StatusPagamentoEnum.PENDENTE) {
                    log.warn("Pagamento {} aprovado no provedor depois de ficar {} na API", pagamento.getId(), atual);
                }
                pagamento.setDataHoraPagamento(situacao.aprovadoEm() == null
                        ? LocalDateTime.now()
                        : situacao.aprovadoEm().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
                boolean comandaFechada = fecharComanda(pagamento);
                atualizarStatus(pagamento, StatusPagamentoEnum.PAGO, comandaFechada);
                cancelarOutroPendente(pagamento);
            }
            case CANCELADO, EXPIRADO -> {
                // Um pagamento aprovado nunca volta para cancelado.
                if (atual == StatusPagamentoEnum.PENDENTE) {
                    atualizarStatus(pagamento, novo == StatusPagamentoEnum.EXPIRADO ? novo : statusDeEncerramento(pagamento), false);
                }
            }
            case RECUSADO -> {
                if (atual == StatusPagamentoEnum.PENDENTE) {
                    pagamento.setMotivoRecusa(limitar(situacao.statusDetalhe(), 80));
                    atualizarStatus(pagamento, StatusPagamentoEnum.RECUSADO, false);
                }
            }
            case ESTORNADO -> {
                log.warn("Pagamento {} da comanda {} foi estornado no provedor; a comanda continua fechada",
                        pagamento.getId(), pagamento.getComanda().getNumero());
                atualizarStatus(pagamento, StatusPagamentoEnum.ESTORNADO, false);
            }
            default -> {
            }
        }
    }

    /** Aprovação tardia (ex.: PIX antigo pago depois de gerado um novo): o pendente que sobrou não deve mais ser pago. */
    private void cancelarOutroPendente(Pagamento aprovado) {
        pagamentoRepository.findFirstByComandaAndStatusOrderByIdDesc(aprovado.getComanda(), StatusPagamentoEnum.PENDENTE)
                .filter(outro -> !outro.getId().equals(aprovado.getId()))
                .ifPresent(this::encerrarPendente);
    }

    private boolean fecharComanda(Pagamento pagamento) {
        Comanda comanda = pagamento.getComanda();
        if (StatusComandaEnum.FECHADA.getValue().equals(comanda.getStatus())) {
            return false;
        }
        // Cartão (digitado ou na conta do Mercado Pago): crédito ou débito, conforme o tipo informado pelo provedor.
        FormaPagamentoEnum forma = pagamento.getMetodo() == MetodoPagamentoEnum.PIX
                ? FormaPagamentoEnum.PIX
                : pagamento.getCartaoTipo() != null ? pagamento.getCartaoTipo() : FormaPagamentoEnum.CREDITO;
        comandaService.fechar(comanda.getNumero(), forma);
        return true;
    }

    private void atualizarStatus(Pagamento pagamento, StatusPagamentoEnum status, boolean comandaFechada) {
        pagamento.setStatus(status);
        pagamento.setDataHoraAtualizacao(LocalDateTime.now());
        pagamentoRepository.save(pagamento);
        log.info("Pagamento {} da comanda {}: {}", pagamento.getId(), pagamento.getComanda().getNumero(), status);
        eventPublisher.publishEvent(new PagamentoAtualizadoEvent(pagamento.getId(), pagamento.getComanda().getNumero(),
                status, comandaFechada));
    }

    private static StatusPagamentoEnum statusDeEncerramento(Pagamento pagamento) {
        return LocalDateTime.now().isAfter(pagamento.getDataHoraExpiracao())
                ? StatusPagamentoEnum.EXPIRADO
                : StatusPagamentoEnum.CANCELADO;
    }

    private Optional<Pagamento> pendenteDa(Comanda comanda) {
        return pagamentoRepository.findFirstByComandaAndStatusOrderByIdDesc(comanda, StatusPagamentoEnum.PENDENTE);
    }

    /** Um link pendente (PIX ou Mercado Pago) é reaproveitado se for do mesmo valor e ainda não estiver expirando. */
    private static boolean reaproveitavel(Pagamento pagamento, MetodoPagamentoEnum metodo, BigDecimal valor) {
        return pagamento.getMetodo() == metodo
                && pagamento.getValor().compareTo(valor) == 0
                && pagamento.getDataHoraExpiracao().isAfter(LocalDateTime.now().plus(MARGEM_REAPROVEITAMENTO));
    }

    private void exigirGateway(String mensagem) {
        if (!gateway.isConfigurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, mensagem);
        }
    }

    private Comanda travarComandaParaPagar(String numero) {
        Comanda comanda = travarComanda(numero);
        if (StatusComandaEnum.FECHADA.getValue().equals(comanda.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta comanda já foi fechada");
        }
        if (!StatusComandaEnum.ABERTA.getValue().equals(comanda.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sua comanda ainda não foi liberada pela equipe");
        }
        return comanda;
    }

    private BigDecimal valorAPagar(Comanda comanda) {
        BigDecimal valor = comandaService.calcularValorTotal(comanda);
        if (valor.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não há valor a pagar nesta comanda");
        }
        return valor;
    }

    private static String descricao(Comanda comanda) {
        return "Comanda " + comanda.getNumero() + " - Covil do Calango";
    }

    /** Confere os dados do formulário; o documento vai só com os dígitos. Nada disso é gravado. */
    private static PagamentoCartaoRequestDTO validarCartao(PagamentoCartaoRequestDTO request) {
        if (request == null || request.getToken() == null || request.getToken().isBlank()
                || request.getToken().length() > TAMANHO_MAXIMO_TOKEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os dados do cartão");
        }
        String bandeira = request.getBandeira() == null ? "" : request.getBandeira().trim().toLowerCase();
        if (!BANDEIRA_VALIDA.matcher(bandeira).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bandeira do cartão inválida");
        }
        String emissor = request.getEmissorId() == null || request.getEmissorId().isBlank() ? null : request.getEmissorId().trim();
        if (emissor != null && !EMISSOR_VALIDO.matcher(emissor).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Emissor do cartão inválido");
        }
        String email = request.getEmail() == null ? "" : request.getEmail().trim();
        if (email.length() > 254 || !EMAIL_VALIDO.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um e-mail válido");
        }
        String tipo = request.getTipoDocumento() == null ? "" : request.getTipoDocumento().trim().toUpperCase();
        String documento = request.getDocumento() == null ? "" : request.getDocumento().replaceAll("\\D", "");
        if (!TIPOS_DOCUMENTO.contains(tipo) || documento.length() != ("CPF".equals(tipo) ? 11 : 14)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o CPF ou CNPJ do titular do cartão");
        }
        return PagamentoCartaoRequestDTO.builder()
                .token(request.getToken().trim())
                .bandeira(bandeira)
                .emissorId(emissor)
                .email(email)
                .tipoDocumento(tipo)
                .documento(documento)
                .build();
    }

    private static Optional<Long> idDaReferencia(String referencia) {
        if (referencia == null || !referencia.startsWith(Pagamento.PREFIXO_REFERENCIA)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(referencia.substring(Pagamento.PREFIXO_REFERENCIA.length())));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String limitar(String valor, int tamanho) {
        return valor == null || valor.length() <= tamanho ? valor : valor.substring(0, tamanho);
    }

    private Comanda travarComanda(String numero) {
        return comandaRepository.findByNumeroParaAtualizacao(numero)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comanda não encontrada"));
    }
}
