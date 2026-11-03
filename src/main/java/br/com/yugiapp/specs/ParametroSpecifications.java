package br.com.yugiapp.specs;

import br.com.yugiapp.dto.ParametroFilterRequestDTO;
import br.com.yugiapp.model.Parametro;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
public class ParametroSpecifications extends DefaultSpecifications<Parametro, ParametroFilterRequestDTO> {

    @Override
    public Specification<Parametro> buildSpecifiction(ParametroFilterRequestDTO filter) {
        String nome = normalize(filter.getNome());
        String descricao = normalize(filter.getDescricao());
        List<Specification<Parametro>> specifications = Arrays.asList(
                getBasicSpecs(),
                wrapSpec(nome, value -> (root, query, builder) ->
                        builder.like(builder.lower(root.get("nome")), "%" + value.toLowerCase() + "%")),
                wrapSpec(descricao, value -> (root, query, builder) ->
                        builder.like(builder.lower(root.get("descricao")), "%" + value.toLowerCase() + "%"))
        );
        return specifications.stream().filter(Objects::nonNull).reduce(Specification::and).orElse(null);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
