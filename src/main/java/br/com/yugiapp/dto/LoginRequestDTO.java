package br.com.yugiapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO implements Serializable {

    private String id;
    private String email;
    private String usuario;
    private String senha;
    /** ID token devolvido pelo login do Google; validado no servidor. */
    private String idToken;
    private String provider;

}
