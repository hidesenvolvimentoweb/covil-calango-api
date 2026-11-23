package br.com.yugiapp.controller;

import br.com.yugiapp.dto.RelatorioDiarioDTO;
import br.com.yugiapp.service.RelatorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Relatórios do Dashboard; rota restrita à permissão WEB_DASHBOARD (JwtSecurityConfig). */
@RestController
@RequestMapping("/api/relatorio")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;

    /** Relatório de um dia (AAAA-MM-DD, no fuso do estabelecimento); sem data, o de hoje. */
    @GetMapping("/diario")
    public RelatorioDiarioDTO diario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return relatorioService.gerarDiario(data == null ? relatorioService.hoje() : data);
    }
}
