package br.com.yugiapp.service;

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
import br.com.yugiapp.service.pagamento.PagamentoAtualizadoEvent;
import br.com.yugiapp.service.pagamento.SituacaoPagamento;
import br.com.yugiapp.service.pagamento.SolicitacaoCobrancaPix;
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
import java.util.UUID;

/**
 * Pagamento da comanda pelo app.
 * <ul>
 *     <li>O valor é sempre calculado no servidor; o app nunca informa quanto vai pagar.</li>
 *     <li>A confirmação vem do provedor: o webhook e a reconciliação periódica apenas disparam uma consulta ao
 *     provedor, cujo resultado é a fonte da verdade.</li>
 *     <li>Com o PIX aprovado a comanda é fechada e o app é avisado em tempo real.</li>
 *     <li>Enquanto houver um PIX pendente, novos pedidos ficam bloqueados (ver PedidoService), para o valor
 *     cobrado não ficar diferente do consumido.</li>
 * </ul>
 * Ordem dos bloqueios, para evitar deadlock entre webhook, reconciliação e geração do PIX: comanda, depois pagamento.
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

    private final PagamentoRepository pagamentoRepository;
    private final ComandaRepository comandaRepository;
    private final ComandaService comandaService;
    private final GatewayPagamento gateway;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Duration expiracaoPix;
    private final String emailPagador;

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

    /**
     * Gera o PIX da comanda, ou devolve o PIX pendente se ele ainda vale para o valor atual.
     * Se a comanda já foi paga nesse meio-tempo, devolve o pagamento aprovado.
     */
    @Transactional
    public PagamentoResponseDTO gerarPix(String numero) {
        if (!gateway.isConfigurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "O pagamento via PIX está indisponível no momento");
        }
        Comanda comanda = travarComanda(numero);
        if (StatusComandaEnum.FECHADA.getValue().equals(comanda.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta comanda já foi fechada");
        }
        if (!StatusComandaEnum.ABERTA.getValue().equals(comanda.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sua comanda ainda não foi liberada pela equipe");
        }
        BigDecimal valor = comandaService.calcularValorTotal(comanda);
        if (valor.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não há valor a pagar nesta comanda");
        }

        try {
            Optional<Pagamento> pendente = pagamentoRepository.findFirstByComandaAndStatusOrderByIdDesc(
                    comanda, StatusPagamentoEnum.PENDENTE);
            if (pendente.isPresent()) {
                Pagamento atual = pendente.get();
                if (atual.getValor().compareTo(valor) == 0
                        && atual.getDataHoraExpiracao().isAfter(LocalDateTime.now().plus(MARGEM_REAPROVEITAMENTO))) {
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

    /** Último pagamento da comanda (o pendente, o aprovado ou a última tentativa). */
    @Transactional(readOnly = true)
    public Optional<PagamentoResponseDTO> consultarUltimo(String numero) {
        return pagamentoRepository.findFirstByComandaNumeroOrderByIdDesc(numero).map(PagamentoResponseDTO::of);
    }

    /**
     * Cancela o PIX pendente da comanda (o cliente desistiu ou a equipe fechou a comanda de outra forma).
     * Se o PIX já tinha sido pago, o pagamento aprovado é registrado e devolvido.
     *
     * @return vazio quando não há PIX pendente
     */
    @Transactional
    public Optional<PagamentoResponseDTO> cancelarPixPendente(String numero) {
        Comanda comanda = travarComanda(numero);
        Optional<Pagamento> pendente = pagamentoRepository.findFirstByComandaAndStatusOrderByIdDesc(
                comanda, StatusPagamentoEnum.PENDENTE);
        if (pendente.isEmpty()) {
            return Optional.empty();
        }
        try {
            encerrarPendente(pendente.get());
        } catch (GatewayPagamentoException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível cancelar o PIX agora. Tente novamente em instantes", exception);
        }
        return pendente.map(PagamentoResponseDTO::of);
    }

    public boolean possuiPixPendente(String numero) {
        return pagamentoRepository.existsByComandaNumeroAndStatus(numero, StatusPagamentoEnum.PENDENTE);
    }

    /**
     * Trata a notificação do provedor sobre o pagamento informado, sempre consultando o provedor.
     * Falhas de comunicação são propagadas para o provedor reenviar a notificação.
     *
     * @return falso se o pagamento não é desta API
     */
    public boolean processarNotificacao(String gatewayId) {
        Optional<Long> id = pagamentoRepository.findIdByGatewayAndGatewayId(gateway.nome(), gatewayId);
        if (id.isEmpty()) {
            log.info("Notificação de pagamento desconhecido ignorada ({} {})", gateway.nome(), gatewayId);
            return false;
        }
        transactionTemplate.executeWithoutResult(status -> sincronizar(id.get(), false));
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
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime expiracao = agora.plus(expiracaoPix);
        Pagamento pagamento = pagamentoRepository.saveAndFlush(Pagamento.builder()
                .comanda(comanda)
                .metodo(MetodoPagamentoEnum.PIX)
                .status(StatusPagamentoEnum.PENDENTE)
                .valor(valor)
                .gateway(gateway.nome())
                .idempotencyKey(UUID.randomUUID().toString())
                .dataHoraCriacao(agora)
                .dataHoraExpiracao(expiracao)
                .dataHoraAtualizacao(agora)
                .build());

        CobrancaPix cobranca = gateway.criarCobrancaPix(new SolicitacaoCobrancaPix(
                pagamento.getReferencia(),
                valor,
                "Comanda " + comanda.getNumero() + " - Covil do Calango",
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

    /** Consulta o provedor e aplica o resultado. Executa dentro de uma transação. */
    private void sincronizar(Long pagamentoId, boolean encerrarSeExpirado) {
        Optional<String> numero = pagamentoRepository.findNumeroComandaById(pagamentoId);
        if (numero.isEmpty()) {
            return;
        }
        travarComanda(numero.get());
        Pagamento pagamento = pagamentoRepository.findByIdParaAtualizacao(pagamentoId).orElseThrow();
        if (pagamento.getGatewayId() == null || pagamento.getStatus() == StatusPagamentoEnum.ESTORNADO) {
            return;
        }
        aplicar(pagamento, gateway.consultar(pagamento.getGatewayId()));
        if (encerrarSeExpirado && pagamento.getStatus() == StatusPagamentoEnum.PENDENTE
                && LocalDateTime.now().isAfter(pagamento.getDataHoraExpiracao().plus(TOLERANCIA_EXPIRACAO))) {
            encerrarPendente(pagamento);
        }
    }

    /**
     * Cancela o PIX no provedor e confere o resultado: se ele foi pago antes do cancelamento, o pagamento é
     * aprovado; caso contrário fica cancelado/expirado.
     */
    private void encerrarPendente(Pagamento pagamento) {
        if (pagamento.getGatewayId() != null) {
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
        if (pagamento.getStatus() == StatusPagamentoEnum.PENDENTE) {
            atualizarStatus(pagamento, statusDeEncerramento(pagamento), false);
        }
        // O próximo PIX da comanda é inserido em seguida; o Hibernate executa inserts antes de updates,
        // e o índice único de pendente por comanda seria violado.
        pagamentoRepository.flush();
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
                boolean comandaFechada = fecharComanda(pagamento.getComanda());
                atualizarStatus(pagamento, StatusPagamentoEnum.PAGO, comandaFechada);
                cancelarOutroPendente(pagamento);
            }
            case CANCELADO, EXPIRADO -> {
                // Um pagamento aprovado nunca volta para cancelado.
                if (atual == StatusPagamentoEnum.PENDENTE) {
                    atualizarStatus(pagamento, novo == StatusPagamentoEnum.EXPIRADO ? novo : statusDeEncerramento(pagamento), false);
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

    /** Aprovação tardia (ex.: PIX antigo pago depois de gerado um novo): o PIX que sobrou não deve mais ser pago. */
    private void cancelarOutroPendente(Pagamento aprovado) {
        pagamentoRepository.findFirstByComandaAndStatusOrderByIdDesc(aprovado.getComanda(), StatusPagamentoEnum.PENDENTE)
                .filter(outro -> !outro.getId().equals(aprovado.getId()))
                .ifPresent(this::encerrarPendente);
    }

    private boolean fecharComanda(Comanda comanda) {
        if (StatusComandaEnum.FECHADA.getValue().equals(comanda.getStatus())) {
            return false;
        }
        comandaService.fechar(comanda.getNumero(), FormaPagamentoEnum.PIX);
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

    private Comanda travarComanda(String numero) {
        return comandaRepository.findByNumeroParaAtualizacao(numero)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comanda não encontrada"));
    }
}
