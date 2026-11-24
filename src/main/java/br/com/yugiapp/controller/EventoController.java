package br.com.yugiapp.controller;

import br.com.yugiapp.dto.EventoFilterRequestDTO;
import br.com.yugiapp.dto.EventoRequestDTO;
import br.com.yugiapp.dto.EventoResponseDTO;
import br.com.yugiapp.service.EventoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Eventos da loja. Consulta: equipe (web-eventos) e clientes do app (app-eventos); cadastro, alteração e exclusão:
 * somente a equipe (ver JwtSecurityConfig).
 */
@RestController
@RequestMapping("/api/evento")
@RequiredArgsConstructor
public class EventoController {

    private final EventoService eventoService;

    /** Eventos do período exibido no calendário ({@code ?inicio=...&fim=...}, data e hora sem fuso). */
    @GetMapping
    public List<EventoResponseDTO> getAll(EventoFilterRequestDTO filter) {
        return eventoService.getAll(filter);
    }

    /** Eventos acontecendo hoje, no fuso do estabelecimento. */
    @GetMapping("/hoje")
    public List<EventoResponseDTO> getAllHoje() {
        return eventoService.getAllHoje();
    }

    @GetMapping(path = "{id}")
    public EventoResponseDTO getById(@PathVariable Long id) {
        return eventoService.getResponseById(id);
    }

    @PostMapping
    public EventoResponseDTO salvar(@RequestBody EventoRequestDTO evento) {
        return eventoService.create(evento);
    }

    @PutMapping(path = "{id}")
    public EventoResponseDTO atualizar(@PathVariable Long id, @RequestBody EventoRequestDTO evento) {
        return eventoService.update(id, evento);
    }

    @DeleteMapping(path = "{id}")
    public void delete(@PathVariable Long id) {
        eventoService.delete(id);
    }
}
