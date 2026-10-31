package br.com.yugiapp.specs;

import br.com.yugiapp.dto.PermissaoFilterRequestDTO;
import br.com.yugiapp.model.Permissao;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
public class PermissaoSpecifications extends DefaultSpecifications<Permissao, PermissaoFilterRequestDTO> {

    @Override
    public Specification<Permissao> buildSpecifiction(PermissaoFilterRequestDTO filter) {
        String name = filter.getName();
        if (name != null && name.isBlank()) {
            name = null;
        } else if (name != null) {
            name = name.trim();
        }
        List<Specification<Permissao>> specifications = Arrays.asList(
                getBasicSpecs(),
                wrapSpec(name, value -> (root, query, builder) ->
                        builder.like(builder.lower(root.get("name")), "%" + value.toLowerCase() + "%"))
        );
        return specifications.stream().filter(Objects::nonNull).reduce(Specification::and).orElse(null);
    }
}
