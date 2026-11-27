package br.com.yugiapp.service;

import br.com.yugiapp.dto.CartoesSalvosDTO;
import br.com.yugiapp.model.CartaoAparelho;
import br.com.yugiapp.repository.CartaoAparelhoRepository;
import br.com.yugiapp.service.pagamento.CartaoSalvo;
import br.com.yugiapp.service.pagamento.GatewayPagamento;
import br.com.yugiapp.service.pagamento.GatewayPagamentoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Cartões salvos no celular do cliente. O app não tem login: ele cria uma chave aleatória do aparelho e a envia no
 * cabeçalho {@link #CABECALHO_CHAVE}. A API guarda só o hash da chave, ligado ao cliente criado no provedor, onde os
 * cartões ficam salvos. Sem a chave (outro celular, app reinstalado), os cartões não aparecem.
 * <p>
 * O e-mail do cliente no provedor é técnico ({@code app.mercadopago.cartao.email-cliente}, com {@code {id}} trocado
 * pelo id do aparelho): o visitante não informa e-mail e ninguém recebe mensagens nele. Com as credenciais de teste do
 * Mercado Pago, use o formato {@code test_payer_{id}@testuser.com}, o único aceito nesse ambiente.
 */
@Slf4j
@Service
public class CartaoSalvoService {

    public static final String CABECALHO_CHAVE = "X-Chave-Aparelho";
    /** Chave gerada pelo app: aleatória, longa e só com caracteres seguros (base64url ou hexadecimal). */
    private static final Pattern CHAVE_VALIDA = Pattern.compile("[A-Za-z0-9_-]{32,128}");
    /** E-mail técnico padrão (o docker-compose passa a variável vazia quando ela não está no .env). */
    static final String MODELO_EMAIL_PADRAO = "cliente-{id}@covildocalango.tech";
    private static final Pattern ID_CARTAO_VALIDO = Pattern.compile("[0-9A-Za-z-]{1,64}");

    private final CartaoAparelhoRepository repository;
    private final GatewayPagamento gateway;
    private final String modeloEmail;

    public CartaoSalvoService(CartaoAparelhoRepository repository, GatewayPagamento gateway,
                              @Value("${app.mercadopago.cartao.email-cliente:cliente-{id}@covildocalango.tech}")
                              String modeloEmail) {
        this.repository = repository;
        this.gateway = gateway;
        this.modeloEmail = modeloEmail == null || !modeloEmail.contains("{id}") ? MODELO_EMAIL_PADRAO : modeloEmail.strip();
    }

    /** Cartões salvos do aparelho; vazio se ele ainda não salvou nenhum. */
    @Transactional
    public CartoesSalvosDTO listar(String chave) {
        Optional<CartaoAparelho> aparelho = aparelho(exigirChave(chave));
        if (aparelho.isEmpty() || aparelho.get().getClienteGatewayId() == null) {
            return new CartoesSalvosDTO(null, List.of());
        }
        CartaoAparelho atual = aparelho.get();
        try {
            List<CartaoSalvo> cartoes = gateway.listarCartoes(atual.getClienteGatewayId());
            return new CartoesSalvosDTO(atual.getClienteGatewayId(), cartoes);
        } catch (GatewayPagamentoException exception) {
            if (exception.getStatusHttp() == 404) {
                esquecerCliente(atual);
                return new CartoesSalvosDTO(null, List.of());
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível carregar os cartões salvos agora", exception);
        }
    }

    /** Remove um cartão salvo do aparelho. */
    @Transactional
    public void excluir(String chave, String cartaoId) {
        if (cartaoId == null || !ID_CARTAO_VALIDO.matcher(cartaoId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cartão inválido");
        }
        CartaoAparelho aparelho = aparelho(exigirChave(chave))
                .filter(atual -> atual.getClienteGatewayId() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cartão não encontrado"));
        try {
            gateway.excluirCartao(aparelho.getClienteGatewayId(), cartaoId);
        } catch (GatewayPagamentoException exception) {
            if (exception.getStatusHttp() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cartão não encontrado", exception);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível remover o cartão agora", exception);
        }
    }

    /**
     * Cliente do provedor com os cartões salvos do aparelho, para a cobrança. Vazio sem chave válida ou se o aparelho
     * ainda não salvou cartões.
     */
    @Transactional
    public Optional<String> clienteDoAparelho(String chave) {
        if (!chaveValida(chave)) {
            return Optional.empty();
        }
        return aparelho(chave).map(atual -> {
            atual.setDataHoraUltimoUso(LocalDateTime.now());
            return atual.getClienteGatewayId();
        });
    }

    /**
     * Salva no aparelho o cartão do token (cria o cliente no provedor na primeira vez). Uma falha só deixa o cartão
     * sem salvar: nunca atrapalha o pagamento. Roda numa transação própria, para um erro aqui não desfazer o
     * pagamento já cobrado.
     *
     * @return se o cartão foi salvo
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean salvar(String chave, String token) {
        if (!chaveValida(chave) || token == null || token.isBlank()) {
            return false;
        }
        try {
            CartaoAparelho aparelho = aparelho(chave).orElseGet(() -> novoAparelho(chave));
            String clienteId = clienteDe(aparelho);
            try {
                gateway.salvarCartao(clienteId, token);
            } catch (GatewayPagamentoException exception) {
                if (exception.getStatusHttp() != 404) {
                    throw exception;
                }
                // O cliente não existe mais no provedor (ex.: troca das credenciais de teste pelas de produção).
                esquecerCliente(aparelho);
                gateway.salvarCartao(clienteDe(aparelho), token);
            }
            aparelho.setDataHoraUltimoUso(LocalDateTime.now());
            return true;
        } catch (GatewayPagamentoException exception) {
            log.warn("Cartão não foi salvo no aparelho: {} (status {})", exception.getMessage(), exception.getStatusHttp());
            return false;
        }
    }

    private String clienteDe(CartaoAparelho aparelho) {
        if (aparelho.getClienteGatewayId() == null) {
            String email = modeloEmail.replace("{id}", String.valueOf(aparelho.getId()));
            aparelho.setClienteGatewayId(gateway.criarCliente(email));
            repository.save(aparelho);
        }
        return aparelho.getClienteGatewayId();
    }

    private void esquecerCliente(CartaoAparelho aparelho) {
        log.info("Cliente {} do aparelho {} não existe mais no provedor; os cartões salvos foram descartados",
                aparelho.getClienteGatewayId(), aparelho.getId());
        aparelho.setClienteGatewayId(null);
        repository.save(aparelho);
    }

    private CartaoAparelho novoAparelho(String chave) {
        LocalDateTime agora = LocalDateTime.now();
        return repository.saveAndFlush(CartaoAparelho.builder()
                .chaveHash(hash(chave))
                .gateway(gateway.nome())
                .dataHoraCriacao(agora)
                .dataHoraUltimoUso(agora)
                .build());
    }

    private Optional<CartaoAparelho> aparelho(String chave) {
        return repository.findByChaveHashAndGateway(hash(chave), gateway.nome());
    }

    private static String exigirChave(String chave) {
        if (!chaveValida(chave)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificação do celular inválida");
        }
        return chave;
    }

    private static boolean chaveValida(String chave) {
        return chave != null && CHAVE_VALIDA.matcher(chave).matches();
    }

    static String hash(String chave) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(chave.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
