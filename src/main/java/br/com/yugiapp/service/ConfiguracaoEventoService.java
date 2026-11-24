package br.com.yugiapp.service;

import br.com.yugiapp.dto.EventoConfiguracaoResponseDTO;
import br.com.yugiapp.model.Parametro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Funcionamento da loja para os eventos, lido da tela de parâmetros. Parâmetro ausente ou com valor inválido usa o
 * padrão (com aviso no log), para que um erro de digitação não derrube o cadastro de eventos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracaoEventoService {

    static final String PARAMETRO_HORA_ABERTURA = "LOJA_HORA_ABERTURA";
    static final String PARAMETRO_DIAS_FECHADOS = "LOJA_DIAS_FECHADOS";
    static final String PARAMETRO_DURACAO_PADRAO = "EVENTO_DURACAO_PADRAO_HORAS";
    static final String PARAMETRO_INTERVALO_HORARIOS = "EVENTO_INTERVALO_HORARIOS_MINUTOS";

    static final LocalTime HORA_ABERTURA_PADRAO = LocalTime.of(14, 0);
    static final Set<DayOfWeek> DIAS_FECHADOS_PADRAO = EnumSet.of(DayOfWeek.MONDAY);
    static final int DURACAO_PADRAO_HORAS = 4;
    static final int INTERVALO_HORARIOS_PADRAO_MINUTOS = 30;

    private static final Map<String, DayOfWeek> DIAS_DA_SEMANA = Map.of(
            "DOMINGO", DayOfWeek.SUNDAY,
            "SEGUNDA", DayOfWeek.MONDAY,
            "TERCA", DayOfWeek.TUESDAY,
            "QUARTA", DayOfWeek.WEDNESDAY,
            "QUINTA", DayOfWeek.THURSDAY,
            "SEXTA", DayOfWeek.FRIDAY,
            "SABADO", DayOfWeek.SATURDAY);

    private final ParametroService parametroService;

    public EventoConfiguracaoResponseDTO getConfiguracao() {
        return EventoConfiguracaoResponseDTO.builder()
                .horaAbertura(getHoraAbertura().toString())
                .diasFechados(getDiasFechados().stream().map(DayOfWeek::getValue).sorted().toList())
                .duracaoPadraoMinutos(getDuracaoPadraoHoras() * 60)
                .intervaloHorariosMinutos(getIntervaloHorariosMinutos())
                .build();
    }

    public LocalTime getHoraAbertura() {
        return valor(PARAMETRO_HORA_ABERTURA).map(texto -> {
            try {
                return LocalTime.parse(texto.length() == 4 ? "0" + texto : texto);
            } catch (DateTimeParseException e) {
                return invalido(PARAMETRO_HORA_ABERTURA, texto, HORA_ABERTURA_PADRAO);
            }
        }).orElse(HORA_ABERTURA_PADRAO);
    }

    /** Dias sem expediente: não é possível cadastrar eventos que comecem neles. */
    public Set<DayOfWeek> getDiasFechados() {
        Optional<Parametro> parametro = parametroService.findByNome(PARAMETRO_DIAS_FECHADOS);
        if (parametro.isEmpty()) {
            return DIAS_FECHADOS_PADRAO;
        }
        // Parâmetro cadastrado e vazio: a loja abre todos os dias.
        String texto = parametro.get().getDescricao();
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        if (texto == null || texto.isBlank()) {
            return dias;
        }
        for (String nome : texto.split("[,;]")) {
            String normalizado = semAcentos(nome).toUpperCase(Locale.ROOT).replace("-FEIRA", "").trim();
            if (normalizado.isEmpty()) {
                continue;
            }
            DayOfWeek dia = DIAS_DA_SEMANA.get(normalizado);
            if (dia == null) {
                log.warn("Dia da semana inválido no parâmetro {}: '{}' (ignorado)", PARAMETRO_DIAS_FECHADOS, nome.trim());
            } else {
                dias.add(dia);
            }
        }
        return dias;
    }

    public int getDuracaoPadraoHoras() {
        return inteiro(PARAMETRO_DURACAO_PADRAO, DURACAO_PADRAO_HORAS, 1, 24);
    }

    public int getIntervaloHorariosMinutos() {
        return inteiro(PARAMETRO_INTERVALO_HORARIOS, INTERVALO_HORARIOS_PADRAO_MINUTOS, 5, 120);
    }

    private int inteiro(String nome, int padrao, int minimo, int maximo) {
        return valor(nome).map(texto -> {
            try {
                int numero = Integer.parseInt(texto);
                return numero >= minimo && numero <= maximo ? numero : invalido(nome, texto, padrao);
            } catch (NumberFormatException e) {
                return invalido(nome, texto, padrao);
            }
        }).orElse(padrao);
    }

    private Optional<String> valor(String nome) {
        return parametroService.findByNome(nome)
                .map(Parametro::getDescricao)
                .map(String::trim)
                .filter(texto -> !texto.isEmpty());
    }

    private static <T> T invalido(String nome, String texto, T padrao) {
        log.warn("Valor inválido no parâmetro {}: '{}'. Usando o padrão {}", nome, texto, padrao);
        return padrao;
    }

    private static String semAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
