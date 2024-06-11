package com.lucastrigueiro.springbootauth.controller;

import com.lucastrigueiro.springbootauth.domain.User;
import com.lucastrigueiro.springbootauth.dto.auth.LoginDto;
import com.lucastrigueiro.springbootauth.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody LoginDto dados) {
        Authentication authenticationToken = new UsernamePasswordAuthenticationToken(dados.login(), dados.password());
//        UsernamePasswordAuthenticationToken é uma implementação da interface Authentication usada especificamente para autenticação baseada em nome de usuário e senha.
//        Esta linha cria um objeto Authentication que ainda não está autenticado, pois apenas contém os dados do usuário, mas ainda não passou pelo processo de verificação.

        Authentication authentication = manager.authenticate(authenticationToken);
//        O manager é uma instância de AuthenticationManager, que é uma interface no Spring Security responsável por gerenciar o processo de autenticação.
//        O método authenticate() é usado para tentar autenticar o Authentication objeto passado como argumento. Se os detalhes de autenticação forem válidos (por exemplo, se o nome de usuário e senha correspondem aos armazenados no sistema), o AuthenticationManager retornará um Authentication objeto totalmente populado, incluindo detalhes como as autorizações do usuário.
//        Se a autenticação falhar (por exemplo, se o nome de usuário ou senha estiverem incorretos), geralmente é lançada uma exceção, como AuthenticationException.

        String tokenJWT = tokenService.generateToken((User) authentication.getPrincipal());
//        O método getPrincipal() retorna o objeto UserDetails associado ao usuário autenticado.

        return ResponseEntity.ok(tokenJWT);
    }

    @PostMapping("/protected")
    public ResponseEntity protectedRoute(@AuthenticationPrincipal User authenticatedUser) {
        return ResponseEntity.ok("Protected route! User: " + authenticatedUser.getLogin() + " - " + authenticatedUser.getPassword());
    }

    // Access only available to users with the ADMIN role
    @PostMapping("/adminRole")
    public ResponseEntity protectedAdminRoleRoute(@AuthenticationPrincipal User authenticatedUser) {
        return ResponseEntity.ok("Protected ADMIN role route! User: " + authenticatedUser.getLogin());
    }

    // Access only available to users with the USER role
    @PostMapping("/userRole")
    public ResponseEntity protectedUserRoleRoute(@AuthenticationPrincipal User authenticatedUser) {
        return ResponseEntity.ok("Protected USER role route! User: " + authenticatedUser.getLogin());
    }

    // Access only available to users with the AUTHORITY_READ1 authority
    @PostMapping("/authorityRead1")
    public ResponseEntity protectedAdminAuthorityRoute(@AuthenticationPrincipal User authenticatedUser) {
        return ResponseEntity.ok("Protected AUTHORITY_READ1 route! User: " + authenticatedUser.getLogin());
    }

    // Access only available to users with the AUTHORITY_READ2 authority
    @PostMapping("/authorityRead2")
    public ResponseEntity protectedUserAuthorityRoute(@AuthenticationPrincipal User authenticatedUser) {
        return ResponseEntity.ok("Protected AUTHORITY_READ2 route! User: " + authenticatedUser.getLogin());
    }

    // Access only available to users with ADMIN or USER role
    @PostMapping("/userOrAdminRole")
    public ResponseEntity protectedUserOrAdminRole(@AuthenticationPrincipal User authenticatedUser) {
        return ResponseEntity.ok("Protected USER or ADMIN role route! User: " + authenticatedUser.getLogin());
    }

    // Access only available to users with AUTHORITY_READ1 or AUTHORITY_READ2 authority
    @PostMapping("/authorityRead1or2")
    public ResponseEntity protectedAuthorityRead1or2(@AuthenticationPrincipal User authenticatedUser) {
        return ResponseEntity.ok("Protected AUTHORITY_READ1 or AUTHORITY_READ2 route! User: " + authenticatedUser.getLogin());
    }

}
