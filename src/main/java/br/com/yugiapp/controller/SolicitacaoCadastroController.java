package br.com.yugiapp.controller;

import br.com.yugiapp.dto.FinalizarCadastroRequestDTO;
import br.com.yugiapp.dto.SolicitacaoCadastroFilterRequestDTO;
import br.com.yugiapp.dto.SolicitacaoCadastroRequestDTO;
import br.com.yugiapp.dto.SolicitacaoCadastroResponseDTO;
import br.com.yugiapp.dto.UsuarioResponseDTO;
import br.com.yugiapp.service.SolicitacaoCadastroService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/solicitacao-cadastro")
@RequiredArgsConstructor
public class SolicitacaoCadastroController {

    private final SolicitacaoCadastroService solicitacaoCadastroService;

    @PostMapping
    public SolicitacaoCadastroResponseDTO solicitar(@RequestBody SolicitacaoCadastroRequestDTO request) {
        return solicitacaoCadastroService.solicitar(request);
    }

    @GetMapping
    public Page<SolicitacaoCadastroResponseDTO> getAll(
            SolicitacaoCadastroFilterRequestDTO filter,
            @PageableDefault(size = 10, sort = "dataHora", direction = Sort.Direction.DESC) Pageable pageable) {
        return solicitacaoCadastroService.getAll(filter, pageable);
    }

    @PostMapping("/{id}/finalizar")
    public UsuarioResponseDTO finalizar(@PathVariable Long id, @RequestBody FinalizarCadastroRequestDTO request) {
        return solicitacaoCadastroService.finalizar(id, request.getPerfilId());
    }

    @DeleteMapping("/{id}")
    public void recusar(@PathVariable Long id) {
        solicitacaoCadastroService.recusar(id);
    }
}
