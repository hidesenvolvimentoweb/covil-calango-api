package br.com.yugiapp.controller;

import br.com.yugiapp.dto.ProdutoFilterRequestDTO;
import br.com.yugiapp.dto.ProdutoRequestDTO;
import br.com.yugiapp.dto.ProdutoResponseDTO;
import br.com.yugiapp.model.Produto;
import br.com.yugiapp.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/produto")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    @PostMapping
    public ProdutoResponseDTO salvar(@RequestBody ProdutoRequestDTO produto) {
        return produtoService.create(produto);
    }

    @PutMapping(path = "{id}")
    public ProdutoResponseDTO atualizar(@PathVariable Long id, @RequestBody ProdutoRequestDTO produto) {
        return produtoService.update(id, produto);
    }

    @DeleteMapping(path = "{id}")
    public void delete(@PathVariable Long id) {
        produtoService.delete(id);
    }

    @GetMapping
    public List<Produto> getAll() {
        return produtoService.getAll();
    }

    @GetMapping("/admin")
    public Page<ProdutoResponseDTO> getAllAdmin(
            ProdutoFilterRequestDTO filter,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return produtoService.getAll(filter, pageable);
    }

    @GetMapping("/admin/{id}")
    public ProdutoResponseDTO getByIdAdmin(@PathVariable Long id) {
        return produtoService.getResponseById(id);
    }

    @GetMapping(path = "{id}")
    public Produto getById(@PathVariable Long id) {
        return produtoService.getById(id);
    }
}
