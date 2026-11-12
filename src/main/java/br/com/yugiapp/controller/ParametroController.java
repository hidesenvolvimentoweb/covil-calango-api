package br.com.yugiapp.controller;

import br.com.yugiapp.dto.ParametroFilterRequestDTO;
import br.com.yugiapp.dto.ParametroRequestDTO;
import br.com.yugiapp.dto.ParametroResponseDTO;
import br.com.yugiapp.service.ParametroService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/parametros")
@RequiredArgsConstructor
public class ParametroController {

    private final ParametroService parametroService;

    @GetMapping("/admin")
    public Page<ParametroResponseDTO> getAllAdmin(
            ParametroFilterRequestDTO filter,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return parametroService.getAll(filter, pageable);
    }

    @GetMapping("/admin/{id}")
    public ParametroResponseDTO getByIdAdmin(@PathVariable Long id) {
        return parametroService.getById(id);
    }

    @PostMapping
    public ParametroResponseDTO create(@RequestBody ParametroRequestDTO request) {
        return parametroService.create(request);
    }

    @PutMapping("/{id}")
    public ParametroResponseDTO update(@PathVariable Long id, @RequestBody ParametroRequestDTO request) {
        return parametroService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        parametroService.delete(id);
    }

    @PostMapping(path = "/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParametroResponseDTO enviarImagem(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return parametroService.atualizarImagem(id, file);
    }

    @DeleteMapping("/{id}/imagem")
    public ParametroResponseDTO removerImagem(@PathVariable Long id) {
        return parametroService.removerImagem(id);
    }

    @GetMapping(path = "/{nome}")
    public ParametroResponseDTO findByNome(@PathVariable String nome) {
        return parametroService.getResponseByNome(nome);
    }
}
