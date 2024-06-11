
# Autenticação JWT com Spring Security

## Propósito
O propósito desses projeto é mostrar de maneira simples como criar a autenticação JWT com o Spring Security.
Portanto, tudo que é vai além disto, foi removido para manter o foco no que é importante.

A ideia é fazer isto com a menor **complexidade** e com a menor **quantidade** de código possível.

## Dependências
O projeto foi inicializado com o [Spring Initializr](https://start.spring.io/) com as seguintes dependências:
- `spring-boot-starter-web`
- `spring-boot-starter-security`
- `lombok` (opcional)
- `java-jwt` (Adicionado manualmente, fora do Spring Initializr)



## Descrição do Código:
Para começarmos, precisamos criar duas funções relacionadas ao JWT:

1. Uma função que tenha a capacidade de gerar um token.

2. Uma função que consiga validar esse token e devolver o Subject.


Essas funções foram desenvolvidas no `TokenService`:

**Geração de Token**:
```java
public String generateToken(User user) {
    try {
        var algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
            .withIssuer(issuer)
            .withSubject(user.getLogin())
            .withExpiresAt(expirationTime())
            .sign(algorithm);
    } catch (JWTCreationException exception){
        throw new RuntimeException("Error generating jwt token", exception);
    }
}
```

**Validação de Token**:

```java
public String validateAndGetSubject(String tokenJWT) {
    try {
        var algorithm = Algorithm.HMAC256(secret);
        return JWT.require(algorithm)
            .withIssuer(issuer)
            .build()
            .verify(tokenJWT)
            .getSubject();
    } catch (JWTVerificationException exception) {
        throw new RuntimeException("Invalid or expired JWT token");
    }
}
```

- No nosso exemplo, o algoritmo escolhido para gerar o token é **HMAC256**, e o segredo é uma **string** que deve ser passada como env.
- Os valores do **tempo de expiração** e o **emissor** (issuer), também são recebidos das envs.
- O **subject** que vai ficar salvo no token pode ser o username, o id ou qualquer coisa que consiga identificar o usuário de forma única.
- Nesse exemplo usamos o **login** como identificador.

### Configuração de Segurança
Agora que já temos as ferramentas para gerar um token e validar um token, podemos começar com o processo de autenticação.

O primeiro passo é definir o que será público e o que será protegido.

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(CsrfConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

Esta configuração:
- Desabilita o **csrf**.
- Define a sessão como **stateless** (desabilita o gerenciamento de sessão).
- Define a rota **"/auth/login"** como aberta (para não exigir o token na rota que gera o token).
- Define as demais rotas como **protegidas**.
- A configuração com **addFilterBefore** será abordada nos passos finais.

### Implementação de Usuário
Os próximos passos são:
1. Criar nosso **User**, implementando o `UserDetails`.
2. Criar um **service** que implemente o `UserDetailsService`.

```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class User implements UserDetails {

    private Long id;
    private String login;
    private String password;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

}
```

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return new User(1L, "lucas", new BCryptPasswordEncoder().encode("123"));
    }
}
```


- Todas as implementações vindas do `UserDetails` foram feitas de forma simples, pois o foco do projeto é apenas a **autenticação JWT**.
- Nesse exemplo, o `User` tem um **id**, um **login** e uma **senha**. Mas poderia ter mais campos e métodos, dependendo da necessidade.


- O `CustomUserDetailsService` implementa o `UserDetailsService`, que obriga a implementação do método `loadUserByUsername`.  Esse método tem a função de recuperar o usuário a partir de um identificador único (username, email, id, etc).


- O `User` também poderia ser uma entidade persistida em um banco de dados usando o **JPA**, por exemplo. É importante ressaltar que se o `User` for salvo em um banco de dados, a **senha** deve ser **criptografada**.
- Nesse nosso exemplo, o `user` é devolvido em uma função que está hardcoded, mas mesmo assim a senha é criptografada usando o `BCryptPasswordEncoder`. Esse foi o algoritmo escolhido para criptografar a senha nesse exemplo, mas poderia ser outro algoritmo.

### Configurações adicionais
Como estamos usando o `BCryptPasswordEncoder`, devemos informar no `SecurityConfigurations` para que o Spring Security consiga fazer a comparação das senhas com o mesmo algoritmo que foi usado para criptografar.

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Um último ponto importante de configuração é a criação do `AuthenticationManager`, que é responsável por fazer a autenticação do usuário.
```java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
}
```




### Login
Pronto, já temos nosso user implementando as funções necessárias, agora podemos iniciar o processo de login.
A primeira coisa a ser feita é criar um DTO para receber os dados da requisição:

```java
public record LoginDto(String login, String password) {
}
```

Então podemos criar o controller:

```java
@PostMapping("/login")
public ResponseEntity login(@RequestBody LoginDto dados) {
    Authentication authenticationToken = new UsernamePasswordAuthenticationToken(dados.login(), dados.password());
    Authentication authentication = manager.authenticate(authenticationToken);

    String tokenJWT = tokenService.generateToken((User) authentication.getPrincipal());

    return ResponseEntity.ok(tokenJWT);
}
```

**UsernamePasswordAuthenticationToken:**
```java
Authentication authenticationToken = new UsernamePasswordAuthenticationToken(dados.login(), dados.password());
```
- `UsernamePasswordAuthenticationToken` é uma implementação da interface `Authentication` usada especificamente para autenticação baseada em nome de usuário e senha.
- Esta linha cria um objeto `Authentication` que ainda não está autenticado, pois apenas contém os dados do usuário, mas ainda não passou pelo processo de verificação.


**AuthenticationManager:**
```java
Authentication authentication = manager.authenticate(authenticationToken);
```
- O `manager` é uma instância de `AuthenticationManager`, que é uma interface no Spring Security responsável por gerenciar o processo de autenticação.
- O método `authenticate()` é usado para tentar autenticar o objeto `Authentication` passado como argumento.
- Se os detalhes de autenticação forem válidos (por exemplo, se o nome de usuário e senha correspondem aos armazenados no sistema), o `AuthenticationManager` retornará um objeto `Authentication` totalmente populado, incluindo detalhes como as autorizações do usuário.
- Se a autenticação falhar (por exemplo, se o nome de usuário ou senha estiverem incorretos), geralmente é lançada uma exceção, como AuthenticationException.


**Geração do token:**
```java
String tokenJWT = tokenService.generateToken((User) authentication.getPrincipal());
```
- O método `getPrincipal()` retorna o objeto UserDetails associado ao usuário autenticado.
- O generateToken é nossa função criada no início do projeto para gerar um token.
- Então o token é devolvido para o cliente.



### Filtro para liberar rotas protegidas
Pronto, já temos o token para autenticar as próximas requisições, mas ao fazer uma requisição protegida, como o servidor vai saber se o token é válido ou não?

Isso será feito com um filtro de segurança, que validará o token e permitirá ou não o acesso à rota.

O token sempre vem no formato `"Bearer token"`, então precisamos remover o `"Bearer "` para termos acesso apenas ao `token`.
Então, antes de criar o filtro, vamos criar um auxiliar para devolver apenas o token:

```java
private String retrieveToken(HttpServletRequest request) {
    var authorizationHeader = request.getHeader("Authorization");
    if (authorizationHeader != null) {
        return authorizationHeader.replace("Bearer ", "");
    }
    return null;
}
```

Agora vamos criar um filtro para validar o token e liberar a rota.

```java
@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var tokenJWT = retrieveToken(request);

        if (tokenJWT != null) {
            String subject = tokenService.validateAndGetSubject(tokenJWT);
            UserDetails user = customUserDetailsService.loadUserByUsername(subject);

            Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

}
```


- Primeiro a função irá extrair o token, depois validar e devolver o subject.
- Depois irá recuperar o usuário com base no identificador guardado como subject.


- Então criará um `authentication` com base no usuário recuperado.
- Por fim esse authentication será adicionado ao contexto do `SecurityContextHolder`, para liberar a requisição em rotas protegidas.


- Observe o que o filtro é chamado em toda requisição, independente se for para uma rota aberta ou uma rota protegida. Porém só adiciona o usuário autenticado ao contexto de segurança se o token for válido.
Ou seja, quem vai fazer a validação se a rota é protegida ou não é o `SecurityFilterChain` que foi configurado no anteriormente no projeto. Então caso seja uma rota aberta ou tenha um usuário autenticado adicionado ao contexto de segurança, e o acesso à rota será permitido. Caso contrário
Porém o `securityFilterChain` é executado antes do `SecurityFilter`, ou seja, a validação irá ocorrer antes ter a autenticação no `SecurityContextHolder`. Portanto, a ordem precisa ser invertida e isso é feito com a seguinte configuração dentro do `securityFilterChain`:

```java
.addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
```

### Rota protegida
Agora que tudo está pronto, basta fazermos um teste com uma rota protegida. Para isso, uma rota de testes foi criada:

```java
@PostMapping("/protected")
    public ResponseEntity protectedRoute(@AuthenticationPrincipal User authenticatedUser) {
    return ResponseEntity.ok("Protected route! User: " + authenticatedUser.getLogin() + " - " + authenticatedUser.getPassword());
}
```

Ela só será acessível se passarmos um token válido no cabeçalho da requisição.
A anotação `@AuthenticationPrincipal`, recupera automáticamente o usuário logado.



# Autorização JWT com Spring Security

## Propósito
O propósito dessa continuação é mostrar de maneira simples como implementar a autorização no nosso projeto base

## Diferença entre Authorities e Roles
Authorities e Roles são usados para controlar o acesso a diferentes partes da aplicação, mas eles têm significados diferentes:

### Authorities
**Authorities** representam as permissões individuais ou privilégios que um usuário possui.
Uma authority pode ser algo como `READ_PRIVILEGE`, `WRITE_PRIVILEGE`, ou qualquer outra permissão granular que controla o acesso a ações específicas dentro da aplicação.
No Spring Security, authorities são expressas como objetos GrantedAuthority, que essencialmente são strings que representam essas permissões.

### Roles
**Roles** são um nível mais alto de abstração e são usados para agrupar várias permissões (authorities) sob uma única etiqueta.
Um role pode ser visto como um conjunto de authorities. Por exemplo, um `ROLE_ADMIN` pode incluir authorities como `READ_PRIVILEGE`, `WRITE_PRIVILEGE`, e `DELETE_PRIVILEGE`.
No Spring Security, quando você define uma role, ela é geralmente prefixada com ROLE_ para distinguir de uma simples authority. Isso ajuda o framework a entender que se trata de uma role e não apenas de uma authority.

## Impementação da autorização
A implementação da autorização será feita em 4 passos:


### 1) Adicionando um novo usuário
Em nosso exemplo tinhamos apenas um usuário hardcoded, agora vamos adicionar um novo usuário para podermos comparar os seus níveis de acesso.
Para nosso exemplo, vamos adicionar um novo usuário chamado `admin` com a senha `123`.

```java
@Override
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    if (username.equals("admin")) {
        return new User(1L, "admin", new BCryptPasswordEncoder().encode("123"));
    } else if (username.equals("lucas")) {
        return new User(2L, "lucas", new BCryptPasswordEncoder().encode("123"));
    }
    throw new UsernameNotFoundException("User not found");
}
```

### 2) Retornando Roles e Authorities específicas para cada usuário
Nesse exemplo, vamos retornar roles e authorities diferentes para cada usuário:
- O usuário **admin** terá a role `ROLE_ADMIN` e a authority `AUTHORITY_READ1`.
- O usuário **lucas** terá a role `ROLE_USER` e a authority `AUTHORITY_READ2`.

```java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    if (login.equals("admin")) {
        return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("AUTHORITY_READ1"));
    }
    return List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("AUTHORITY_READ2"));
}
```






### 3) Definindo as rotas para cada nível de acesso
Primeiro, vamos definir as rotas que serão protegidas de acordo com o nível de acesso do usuário.
Nesse exemplo, vamos definir as seguintes rotas:


```java
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
```
*Note que até o momento as rotas foram apenas definidas e não estão realmente protegidas. A proteção será feita no próximo passo.*

### 4) Adicionando a proteção das rotas para cada nível de acesso

Nesse exemplo temos:
- `/auth/adminRole`: Rota que requerem uma role `ROLE_ADMIN`.
- `/auth/userRole`: Rota que requerem uma role `ROLE_USER`.
- `/auth/authorityRead1`: Rota que requerem uma authority `AUTHORITY_READ1`.
- `/auth/authorityRead2`: Rota que requerem uma authority `AUTHORITY_READ2`.
- `/auth/userOrAdminRole`: Rota que requerem uma role `ROLE_ADMIN` ou uma role `ROLE_USER`.
- `/auth/authorityRead1or2`: Rota que requerem uma authority `AUTHORITY_READ1` ou uma authority `AUTHORITY_READ2`.

```java
.authorizeHttpRequests(auth -> auth
    // Allow access to the login route
    .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()

    // Allow access according to the user's role
    .requestMatchers(HttpMethod.POST, "/auth/adminRole").hasRole("ADMIN")
    .requestMatchers(HttpMethod.POST, "/auth/userRole").hasRole("USER")

    // Allow access according to the user's authority
    .requestMatchers(HttpMethod.POST, "/auth/authorityRead1").hasAuthority("AUTHORITY_READ1")
    .requestMatchers(HttpMethod.POST, "/auth/authorityRead2").hasAuthority("AUTHORITY_READ2")

    // Allows access according to multiple user roles
    .requestMatchers(HttpMethod.POST, "/auth/userOrAdminRole").hasAnyRole("ADMIN", "USER")
    // Allows access according to multiple user authorities
    .requestMatchers(HttpMethod.POST, "/auth/authorityRead1or2").hasAnyAuthority("AUTHORITY_READ1", "AUTHORITY_READ2")

    // Require authentication for all other routes
    .anyRequest().authenticated()
)
```


**Observações:**
- Note que o uso do prefixo `ROLE_` é o que define se é uma **role** ou uma **authority**.
  No momento de usar o `hasRole`, o valor deve ser passado sem o prefixo. Caso o prefixo seja passado acontecerá um erro semelhante a este:
  `ROLE_ADMIN should not start with ROLE_ since ROLE_ is automatically prepended when using hasAnyRole`.

- Usar uma *role* como *authority* não é uma prática recomendada, porém não causa erro. Por exemplo: `.hasAuthority("ROLE_USER")` é válido.

