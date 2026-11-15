package br.com.yugiapp.service;

import br.com.yugiapp.config.security.JwtTokenService;
import br.com.yugiapp.dto.SolicitacaoRequestDTO;
import br.com.yugiapp.enums.StatusSolicitacaoEnum;
import br.com.yugiapp.model.Solicitacao;
import br.com.yugiapp.repository.SolicitacaoRepository;
import br.com.yugiapp.specs.SolicitacaoSpecifications;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolicitacaoServiceTest {

    private static final String REQUEST_ID = "0f8fad5b-d9cb-469f-a165-70867728950e";

    private final SolicitacaoRepository repository = mock(SolicitacaoRepository.class);
    private SolicitacaoService service;

    @BeforeEach
    void configurar() {
        service = new SolicitacaoService(repository, mock(ComandaService.class), mock(UsuarioService.class),
                new JwtTokenService("segredo-de-teste-com-mais-de-32-caracteres", 4, 24),
                mock(SolicitacaoSpecifications.class));
        when(repository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private SolicitacaoRequestDTO solicitacao(String nome, String requestId) {
        return SolicitacaoRequestDTO.builder().usuario(nome).requestId(requestId).build();
    }

    private void assertBadRequest(SolicitacaoRequestDTO solicitacao) {
        assertThatThrownBy(() -> service.solicitar(solicitacao))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sessao-1", "nao-eh-uuid"})
    void exigeRequestIdUuid(String requestId) {
        assertBadRequest(solicitacao("Ana", requestId));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\"\""})
    void exigeNome(String nome) {
        assertBadRequest(solicitacao(nome, REQUEST_ID));
    }

    @Test
    void recusaNomeLongo() {
        assertBadRequest(solicitacao("a".repeat(SolicitacaoService.TAMANHO_MAXIMO_NOME + 1), REQUEST_ID));
    }

    @Test
    void criaSolicitacaoValida() {
        Solicitacao criada = service.solicitar(solicitacao("  \"Ana\" ", REQUEST_ID)).orElseThrow();
        assertThat(criada.getNome()).isEqualTo("Ana");
        assertThat(criada.getSessionId()).isEqualTo(REQUEST_ID);
        assertThat(criada.getStatus()).isEqualTo(StatusSolicitacaoEnum.EM_ANALISE.getValue());
    }

    @Test
    void reenvioDoMesmoRequestIdNaoCriaOutraSolicitacao() {
        when(repository.existsBySessionId(REQUEST_ID)).thenReturn(true);
        assertThat(service.solicitar(solicitacao("Ana", REQUEST_ID))).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void naoRecusaSolicitacaoJaRespondida() {
        Solicitacao aceita = Solicitacao.builder().id(1L).status(StatusSolicitacaoEnum.ACEITA.getValue()).build();
        assertThatThrownBy(() -> service.recusarSolicitacao(aceita))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).save(any());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void recusaSolicitacaoEmAnalise() {
        Solicitacao emAnalise = Solicitacao.builder().id(1L).status(StatusSolicitacaoEnum.EM_ANALISE.getValue()).build();
        assertThat(service.recusarSolicitacao(emAnalise).getStatus()).isEqualTo(StatusSolicitacaoEnum.RECUSADA.getValue());
    }

    @Test
    void dadosPessoaisESessaoNaoSaemNoJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String solicitacao = mapper.writeValueAsString(Solicitacao.builder().sessionId(REQUEST_ID).nome("Ana").build());
        assertThat(solicitacao).doesNotContain(REQUEST_ID).doesNotContain("sessionId");

        String comanda = mapper.writeValueAsString(br.com.yugiapp.model.Comanda.builder()
                .numero("202615-10").celular("5583999999999").notificarWhatsapp(true).build());
        assertThat(comanda).doesNotContain("5583999999999").doesNotContain("celular").doesNotContain("notificarWhatsapp");
    }
}
