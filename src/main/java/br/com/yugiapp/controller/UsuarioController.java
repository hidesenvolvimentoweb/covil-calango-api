package br.com.yugiapp.controller;

import br.com.yugiapp.dto.UsuarioRequestDTO;
import br.com.yugiapp.dto.UsuarioFilterRequestDTO;
import br.com.yugiapp.dto.UsuarioResponseDTO;
import br.com.yugiapp.model.Usuario;
import br.com.yugiapp.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping
    public UsuarioResponseDTO salvar(@RequestBody UsuarioRequestDTO request) {
        Usuario usuario = usuarioService.create(request);
        return usuarioService.getResponseById(usuario.getId());
    }

    @GetMapping
    public Page<UsuarioResponseDTO> getAll(
            UsuarioFilterRequestDTO filter,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return usuarioService.getAllResponses(filter, pageable);
    }

    @GetMapping("/{id}")
    public UsuarioResponseDTO getById(@PathVariable Long id) {
        return usuarioService.getResponseById(id);
    }

    @PutMapping("/{id}")
    public UsuarioResponseDTO editar(@PathVariable Long id, @RequestBody UsuarioRequestDTO request) {
        Usuario usuario = usuarioService.update(id, request);
        return usuarioService.getResponseById(usuario.getId());
    }

    @PutMapping
    public UsuarioResponseDTO editar(@RequestBody UsuarioRequestDTO request) {
        Usuario usuario = usuarioService.update(request.getId(), request);
        return usuarioService.getResponseById(usuario.getId());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        usuarioService.delete(id);
    }

    @PostMapping("/{usuarioId}/perfis/{perfilId}")
    public UsuarioResponseDTO associateProfile(@PathVariable Long usuarioId, @PathVariable Long perfilId) {
        Usuario usuario = usuarioService.associateProfile(usuarioId, perfilId);
        return usuarioService.getResponseById(usuario.getId());
    }

    @DeleteMapping("/{usuarioId}/perfis/{perfilId}")
    public UsuarioResponseDTO disassociateProfile(@PathVariable Long usuarioId, @PathVariable Long perfilId) {
        Usuario usuario = usuarioService.disassociateProfile(usuarioId, perfilId);
        return usuarioService.getResponseById(usuario.getId());
    }

}
