package br.com.yugiapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedefinirSenhaRequestDTO {

    private String token;
    /** Obrigatória apenas no primeiro acesso (usuário ainda com a senha temporária enviada por e-mail). */
    private String senhaTemporaria;
    private String password;
}
