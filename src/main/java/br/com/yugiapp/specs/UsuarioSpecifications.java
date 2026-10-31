package br.com.yugiapp.specs;

import br.com.yugiapp.dto.UsuarioFilterRequestDTO;
import br.com.yugiapp.model.Usuario;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
public class UsuarioSpecifications extends DefaultSpecifications<Usuario, UsuarioFilterRequestDTO> {

    @Override
    public Specification<Usuario> buildSpecifiction(UsuarioFilterRequestDTO filter) {
        List<Specification<Usuario>> specifications = Arrays.asList(
                getBasicSpecs(),
                wrapSpec(normalize(filter.getNome()), value -> containsIgnoreCase(value, "nome")),
                wrapSpec(normalize(filter.getEmail()), value -> containsIgnoreCase(value, "email")),
                wrapSpec(normalize(filter.getContato()), value -> containsIgnoreCase(value, "contato"))
        );
        return specifications.stream().filter(Objects::nonNull).reduce(Specification::and).orElse(null);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Specification<Usuario> containsIgnoreCase(String value, String attribute) {
        return (root, query, builder) ->
                builder.like(builder.lower(root.get(attribute)), "%" + value.toLowerCase() + "%");
    }
}
