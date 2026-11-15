package br.com.yugiapp.controller;

import br.com.yugiapp.config.security.AuthCookieService;
import br.com.yugiapp.config.security.JwtTokenService;
import br.com.yugiapp.dto.EsqueciSenhaRequestDTO;
import br.com.yugiapp.dto.LoginRequestDTO;
import br.com.yugiapp.dto.PerfilResponseDTO;
import br.com.yugiapp.dto.RedefinirSenhaRequestDTO;
import br.com.yugiapp.dto.UsuarioResponseDTO;
import br.com.yugiapp.service.AuthService;
import br.com.yugiapp.service.RecuperacaoSenhaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final JwtTokenService jwtTokenService;
    private final AuthCookieService authCookieService;

    /**
     * Autentica o usuário e grava o JWT da sessão em um cookie HttpOnly. O token não vai no corpo
     * da resposta, para que o JavaScript da página nunca tenha acesso a ele.
     */
    @PostMapping("/login")
    public ResponseEntity<UsuarioResponseDTO> login(@RequestBody LoginRequestDTO loginRequestDTO) {
        UsuarioResponseDTO usuario = authService.login(loginRequestDTO);
        List<String> perfis = usuario.getPerfis() == null ? List.of() : usuario.getPerfis().stream()
                .map(PerfilResponseDTO::getName)
                .filter(Objects::nonNull)
                .toList();
        String token = jwtTokenService.generateToken(usuario.getEmail(), perfis);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieService.criar(token, jwtTokenService.getValidade()).toString(),
                        authCookieService.limparLegado().toString())
                .body(usuario);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookieService.limpar().toString(),
                        authCookieService.limparLegado().toString())
                .build();
    }

    @PostMapping("/esqueci-senha")
    public void esqueciSenha(@RequestBody EsqueciSenhaRequestDTO request) {
        recuperacaoSenhaService.solicitar(request.getEmail());
    }

    @PostMapping("/redefinir-senha")
    public void redefinirSenha(@RequestBody RedefinirSenhaRequestDTO request) {
        recuperacaoSenhaService.redefinir(request.getToken(), request.getSenhaTemporaria(), request.getPassword());
    }
}
