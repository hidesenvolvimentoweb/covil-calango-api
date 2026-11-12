package br.com.yugiapp.service;

import br.com.yugiapp.dto.SolicitacaoCadastroFilterRequestDTO;
import br.com.yugiapp.dto.SolicitacaoCadastroRequestDTO;
import br.com.yugiapp.dto.SolicitacaoCadastroResponseDTO;
import br.com.yugiapp.dto.UsuarioResponseDTO;
import br.com.yugiapp.model.Perfil;
import br.com.yugiapp.model.SolicitacaoCadastro;
import br.com.yugiapp.model.Usuario;
import br.com.yugiapp.repository.PerfilRepository;
import br.com.yugiapp.repository.SolicitacaoCadastroRepository;
import br.com.yugiapp.specs.SolicitacaoCadastroSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SolicitacaoCadastroService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SolicitacaoCadastroRepository solicitacaoCadastroRepository;
    private final SolicitacaoCadastroSpecifications solicitacaoCadastroSpecifications;
    private final UsuarioService usuarioService;
    private final PerfilRepository perfilRepository;
    private final RecuperacaoSenhaService recuperacaoSenhaService;

    @Transactional
    public SolicitacaoCadastroResponseDTO solicitar(SolicitacaoCadastroRequestDTO request) {
        String nome = normalize(request.getNome());
        String email = normalize(request.getEmail());
        if (nome == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um e-mail válido");
        }
        email = email.toLowerCase();
        if (usuarioService.getByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        if (solicitacaoCadastroRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe uma solicitação de cadastro em análise para este e-mail");
        }

        return toResponse(solicitacaoCadastroRepository.save(SolicitacaoCadastro.builder()
                .nome(nome)
                .email(email)
                .contato(normalize(request.getContato()))
                .dataHora(LocalDateTime.now())
                .build()));
    }

    @Transactional(readOnly = true)
    public Page<SolicitacaoCadastroResponseDTO> getAll(SolicitacaoCadastroFilterRequestDTO filter, Pageable pageable) {
        return solicitacaoCadastroRepository.findAll(solicitacaoCadastroSpecifications.getSpecs(filter), pageable)
                .map(this::toResponse);
    }

    /**
     * Finaliza o cadastro: cria o usuário com o perfil inicial escolhido, remove a solicitação e envia
     * por e-mail a senha temporária com o link de troca de senha. Se o e-mail não puder ser enviado,
     * a transação é desfeita e a solicitação continua pendente.
     */
    @Transactional
    public UsuarioResponseDTO finalizar(Long id, Long perfilId) {
        SolicitacaoCadastro solicitacao = getById(id);
        if (perfilId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione o perfil inicial do usuário");
        }
        Perfil perfil = perfilRepository.findById(perfilId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil não encontrado"));
        if (usuarioService.getByEmail(solicitacao.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }

        solicitacaoCadastroRepository.delete(solicitacao);
        Usuario usuario = recuperacaoSenhaService.enviarPrimeiroAcesso(Usuario.builder()
                .nome(solicitacao.getNome())
                .contato(solicitacao.getContato())
                .email(solicitacao.getEmail())
                .perfis(new ArrayList<>(List.of(perfil)))
                .build());
        return usuarioService.getResponseById(usuario.getId());
    }

    @Transactional
    public void recusar(Long id) {
        solicitacaoCadastroRepository.delete(getById(id));
    }

    private SolicitacaoCadastro getById(Long id) {
        return solicitacaoCadastroRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Solicitação de cadastro não encontrada"));
    }

    private SolicitacaoCadastroResponseDTO toResponse(SolicitacaoCadastro solicitacao) {
        return SolicitacaoCadastroResponseDTO.builder()
                .id(solicitacao.getId())
                .nome(solicitacao.getNome())
                .contato(solicitacao.getContato())
                .email(solicitacao.getEmail())
                .dataHora(solicitacao.getDataHora())
                .build();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
