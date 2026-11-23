package br.com.yugiapp.service;

import br.com.yugiapp.converter.ComandaConverter;
import br.com.yugiapp.enums.FormaPagamentoEnum;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.repository.ComandaRepository;
import br.com.yugiapp.repository.PagamentoRepository;
import br.com.yugiapp.specs.ComandaSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComandaServiceTest {

    private static final String COMANDA = "202623-10";

    private final ComandaRepository comandaRepository = mock(ComandaRepository.class);
    private ComandaService service;

    @BeforeEach
    void configurar() {
        service = new ComandaService(comandaRepository, mock(ComandaSpecifications.class), mock(ComandaConverter.class),
                mock(UsuarioService.class), mock(PedidoService.class), mock(PagamentoRepository.class));
        when(comandaRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private void comandaComStatus(StatusComandaEnum status) {
        when(comandaRepository.findByNumeroParaAtualizacao(COMANDA))
                .thenReturn(Optional.of(Comanda.builder().numero(COMANDA).status(status.getValue()).build()));
    }

    @ParameterizedTest
    @EnumSource(FormaPagamentoEnum.class)
    void fechaRegistrandoAFormaDePagamento(FormaPagamentoEnum forma) {
        comandaComStatus(StatusComandaEnum.ABERTA);

        Comanda fechada = service.fechar(COMANDA, forma);

        assertThat(fechada.getStatus()).isEqualTo(StatusComandaEnum.FECHADA.getValue());
        assertThat(fechada.getFormaPagamento()).isEqualTo(forma);
        assertThat(fechada.getDataHoraSaida()).isNotNull();
    }

    @Test
    void naoFechaDuasVezes() {
        comandaComStatus(StatusComandaEnum.FECHADA);

        assertThatThrownBy(() -> service.fechar(COMANDA, FormaPagamentoEnum.DINHEIRO))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void exigeAFormaDePagamento() {
        assertThatThrownBy(() -> service.fechar(COMANDA, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void comandaDoVisitanteNaoFicaLigadaANenhumUsuario() {
        when(comandaRepository.save(any())).thenAnswer(invocacao -> {
            Comanda comanda = invocacao.getArgument(0);
            if (comanda.getId() == null) {
                comanda.setId(42L);
            }
            return comanda;
        });

        Comanda comanda = service.gerarComanda("Ana");

        assertThat(comanda.getUsuario()).isNull();
        assertThat(comanda.getNomeCliente()).isEqualTo("Ana");
        assertThat(comanda.getNumero()).endsWith("-42");
    }

    @Test
    void numeroDaComandaUsaODiaDoEstabelecimento() {
        // Sem a propriedade (como neste teste), vale o fuso de Brasília, igual ao app.fuso-horario padrão.
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        assertThat(service.numeroDaComanda(7L)).isEqualTo(String.valueOf(hoje.getYear()) + hoje.getDayOfMonth() + "-7");
    }
}
