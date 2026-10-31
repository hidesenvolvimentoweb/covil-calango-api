package br.com.yugiapp.controller;

import br.com.yugiapp.dto.PerfilResponseDTO;
import br.com.yugiapp.dto.PermissaoRequestDTO;
import br.com.yugiapp.dto.PermissaoFilterRequestDTO;
import br.com.yugiapp.dto.PermissaoResponseDTO;
import br.com.yugiapp.service.PermissaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/permissao")
@RequiredArgsConstructor
public class PermissaoController {

    private final PermissaoService permissaoService;

    @PostMapping
    public PermissaoResponseDTO create(@RequestBody PermissaoRequestDTO request) {
        return permissaoService.create(request);
    }

    @GetMapping
    public Page<PermissaoResponseDTO> getAll(
            PermissaoFilterRequestDTO filter,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return permissaoService.getAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public PermissaoResponseDTO getById(@PathVariable Long id) {
        return permissaoService.getById(id);
    }

    @PutMapping("/{id}")
    public PermissaoResponseDTO update(@PathVariable Long id, @RequestBody PermissaoRequestDTO request) {
        return permissaoService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        permissaoService.delete(id);
    }

    @PostMapping("/{permissaoId}/perfis/{perfilId}")
    public PerfilResponseDTO associateWithProfile(@PathVariable Long permissaoId, @PathVariable Long perfilId) {
        return permissaoService.associateWithProfile(permissaoId, perfilId);
    }

    @DeleteMapping("/{permissaoId}/perfis/{perfilId}")
    public PerfilResponseDTO disassociateFromProfile(@PathVariable Long permissaoId, @PathVariable Long perfilId) {
        return permissaoService.disassociateFromProfile(permissaoId, perfilId);
    }
}
