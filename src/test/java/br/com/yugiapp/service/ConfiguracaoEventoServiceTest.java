package br.com.yugiapp.service;

import br.com.yugiapp.dto.EventoConfiguracaoResponseDTO;
import br.com.yugiapp.model.Parametro;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfiguracaoEventoServiceTest {

    private final ParametroService parametroService = mock(ParametroService.class);
    private final ConfiguracaoEventoService service = new ConfiguracaoEventoService(parametroService);

    private void parametro(String nome, String valor) {
        when(parametroService.findByNome(nome))
                .thenReturn(Optional.of(Parametro.builder().nome(nome).descricao(valor).build()));
    }

    @Test
    void semParametrosUsaOsPadroes() {
        when(parametroService.findByNome(anyString())).thenReturn(Optional.empty());

        EventoConfiguracaoResponseDTO configuracao = service.getConfiguracao();

        assertThat(configuracao.getHoraAbertura()).isEqualTo("14:00");
        assertThat(configuracao.getDiasFechados()).containsExactly(1);
        assertThat(configuracao.getDuracaoPadraoMinutos()).isEqualTo(240);
        assertThat(configuracao.getIntervaloHorariosMinutos()).isEqualTo(30);
    }

    @Test
    void leOsParametrosCadastrados() {
        parametro(ConfiguracaoEventoService.PARAMETRO_HORA_ABERTURA, " 9:30 ");
        parametro(ConfiguracaoEventoService.PARAMETRO_DIAS_FECHADOS, "segunda-feira, Terça; DOMINGO");
        parametro(ConfiguracaoEventoService.PARAMETRO_DURACAO_PADRAO, "3");
        parametro(ConfiguracaoEventoService.PARAMETRO_INTERVALO_HORARIOS, "15");

        EventoConfiguracaoResponseDTO configuracao = service.getConfiguracao();

        assertThat(configuracao.getHoraAbertura()).isEqualTo("09:30");
        assertThat(configuracao.getDiasFechados()).containsExactly(1, 2, 7);
        assertThat(configuracao.getDuracaoPadraoMinutos()).isEqualTo(180);
        assertThat(configuracao.getIntervaloHorariosMinutos()).isEqualTo(15);
    }

    @Test
    void valoresInvalidosUsamOPadrao() {
        parametro(ConfiguracaoEventoService.PARAMETRO_HORA_ABERTURA, "14h");
        parametro(ConfiguracaoEventoService.PARAMETRO_DIAS_FECHADOS, "SEGUNDA, FERIADO");
        parametro(ConfiguracaoEventoService.PARAMETRO_DURACAO_PADRAO, "0");
        parametro(ConfiguracaoEventoService.PARAMETRO_INTERVALO_HORARIOS, "meia hora");

        assertThat(service.getHoraAbertura()).isEqualTo(LocalTime.of(14, 0));
        assertThat(service.getDiasFechados()).containsExactly(DayOfWeek.MONDAY);
        assertThat(service.getDuracaoPadraoHoras()).isEqualTo(4);
        assertThat(service.getIntervaloHorariosMinutos()).isEqualTo(30);
    }

    @Test
    void diasFechadosVazioSignificaQueALojaAbreTodosOsDias() {
        parametro(ConfiguracaoEventoService.PARAMETRO_DIAS_FECHADOS, " ");
        assertThat(service.getConfiguracao().getDiasFechados()).isEqualTo(List.of());
    }
}
