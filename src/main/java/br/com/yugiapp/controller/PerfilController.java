package br.com.yugiapp.controller;

import br.com.yugiapp.dto.PerfilRequestDTO;
import br.com.yugiapp.dto.PerfilFilterRequestDTO;
import br.com.yugiapp.dto.PerfilResponseDTO;
import br.com.yugiapp.service.PerfilService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfilService;

    @PostMapping
    public PerfilResponseDTO create(@RequestBody PerfilRequestDTO request) {
        return perfilService.create(request);
    }

    @GetMapping
    public Page<PerfilResponseDTO> getAll(
            PerfilFilterRequestDTO filter,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return perfilService.getAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public PerfilResponseDTO getById(@PathVariable Long id) {
        return perfilService.getById(id);
    }

    @PutMapping("/{id}")
    public PerfilResponseDTO update(@PathVariable Long id, @RequestBody PerfilRequestDTO request) {
        return perfilService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        perfilService.delete(id);
    }
}
