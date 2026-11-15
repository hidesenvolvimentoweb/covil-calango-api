package br.com.yugiapp.service;

import br.com.yugiapp.config.security.JwtTokenService;
import br.com.yugiapp.dto.SolicitacaoFilterRequestDTO;
import br.com.yugiapp.dto.SolicitacaoRequestDTO;
import br.com.yugiapp.enums.StatusComandaEnum;
import br.com.yugiapp.enums.StatusSolicitacaoEnum;
import br.com.yugiapp.model.Comanda;
import br.com.yugiapp.model.Solicitacao;
import br.com.yugiapp.model.Usuario;
import br.com.yugiapp.repository.SolicitacaoRepository;
import br.com.yugiapp.specs.SolicitacaoSpecifications;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SolicitacaoService {

    private final SolicitacaoRepository solicitacaoRepository;
    private final ComandaService comandaService;
    private final UsuarioService usuarioService;
    private final JwtTokenService jwtTokenService;

    private final SolicitacaoSpecifications solicitacaoSpecifications;

    public List<Solicitacao> getAll() {
        return solicitacaoRepository.findAll();
    }

    public List<Solicitacao> getAllByFilters(SolicitacaoFilterRequestDTO solicitacaoFilterRequestDTO) {
        return solicitacaoRepository.findAll(solicitacaoSpecifications.buildSpecifiction(solicitacaoFilterRequestDTO));
    }

    public Optional<Solicitacao> findBySessionId(String id) {
        return solicitacaoRepository.findBySessionId(id);
    }

    public Solicitacao getById(Long id) {
        return solicitacaoRepository.findById(id).orElseThrow();
    }

    public void deleteById(Long id) {
        solicitacaoRepository.deleteById(id);
    }

    public Solicitacao buildNovaSolicitacao(SolicitacaoRequestDTO solicitacaoRequestDTO, String sessionId) {
        String requestId = Optional.ofNullable(solicitacaoRequestDTO.getRequestId())
                .filter(id -> !id.isBlank())
                .map(UUID::fromString)
                .map(UUID::toString)
                .orElse(sessionId);
        // Só guarda o celular quando o cliente consentiu em receber as notificações e o número é válido.
        Optional<String> celular = Boolean.TRUE.equals(solicitacaoRequestDTO.getNotificarWhatsapp())
                ? WhatsappService.normalizarCelular(solicitacaoRequestDTO.getCelular())
                : Optional.empty();
        return Solicitacao.builder()
                .nome(solicitacaoRequestDTO.getUsuario().replaceAll("\"", ""))
                .email(solicitacaoRequestDTO.getEmail())
                .sessionId(requestId)
                .dataHora(LocalDateTime.now())
                .status(StatusSolicitacaoEnum.EM_ANALISE.getValue())
                .celular(celular.orElse(null))
                .notificarWhatsapp(celular.isPresent())
                .build();
    }

    public Solicitacao save(Solicitacao solicitacao) {
        return solicitacaoRepository.save(solicitacao);
    }

    @Transactional
    public Solicitacao alterarStatus(Solicitacao solicitacao, StatusSolicitacaoEnum statusSolicitacaoEnum) {
        solicitacao.setStatus(statusSolicitacaoEnum.getValue());
        return save(solicitacao);
    }

    @Transactional
    public Solicitacao aceitarSolicitacao(Solicitacao solicitacao) {
        // Evita gerar uma segunda comanda se a solicitação já foi respondida (ex.: dois atendentes ao mesmo tempo).
        if (!StatusSolicitacaoEnum.EM_ANALISE.getValue().equals(solicitacao.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta solicitação já foi respondida");
        }
        Comanda comandaVisitante = comandaService.gerarComanda(Usuario.builder().nome(solicitacao.getNome()).id(1L).build());
        comandaVisitante.setCelular(solicitacao.getCelular());
        comandaVisitante.setNotificarWhatsapp(solicitacao.isNotificarWhatsapp());
        comandaService.save(comandaVisitante);
        Solicitacao solicitacaoAceita = alterarStatus(solicitacao, StatusSolicitacaoEnum.ACEITA);
        solicitacaoAceita.setComanda(comandaService.alterarStatus(comandaVisitante.getNumero(), StatusComandaEnum.ABERTA));
        return comToken(save(solicitacaoAceita));
    }

    /**
     * Anexa o token do visitante à solicitação aceita cuja comanda ainda está aberta. Usado no aceite e quando
     * o app se inscreve novamente no tópico (ex.: perdeu a conexão no momento do aceite).
     */
    public Solicitacao comToken(Solicitacao solicitacao) {
        Comanda comanda = solicitacao.getComanda();
        if (StatusSolicitacaoEnum.ACEITA.getValue().equals(solicitacao.getStatus()) && comanda != null
                && !StatusComandaEnum.FECHADA.getValue().equals(comanda.getStatus())) {
            solicitacao.setToken(jwtTokenService.generateTokenVisitante(solicitacao.getNome(), comanda.getNumero()));
        }
        return solicitacao;
    }

    public Solicitacao recusarSolicitacao(Solicitacao solicitacao) {
        if (!StatusSolicitacaoEnum.EM_ANALISE.getValue().equals(solicitacao.getStatus())) {
            deleteById(solicitacao.getId());
        }
        return alterarStatus(solicitacao, StatusSolicitacaoEnum.RECUSADA);
    }

}
