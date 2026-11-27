package br.com.yugiapp.controller;

import br.com.yugiapp.dto.CozinhaDTO;
import br.com.yugiapp.service.CozinhaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Situação da cozinha: o cardápio do app mostra se ela está aberta e a equipe abre ou fecha pela tela de Pedidos.
 * Cada alteração é avisada em {@value #TOPICO_COZINHA}, para o cardápio atualizar na hora.
 */
@RestController
@RequestMapping("/api/cozinha")
@RequiredArgsConstructor
public class CozinhaController {

    public static final String TOPICO_COZINHA = "/topic/cozinha";

    private final CozinhaService cozinhaService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public CozinhaDTO get() {
        return new CozinhaDTO(cozinhaService.isAberta());
    }

    @PutMapping
    public CozinhaDTO alterar(@RequestBody CozinhaDTO request) {
        if (request == null || request.aberta() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe se a cozinha está aberta");
        }
        CozinhaDTO cozinha = new CozinhaDTO(cozinhaService.definirAberta(request.aberta()));
        messagingTemplate.convertAndSend(TOPICO_COZINHA, cozinha);
        return cozinha;
    }
}
