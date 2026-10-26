# Projeto Covil do Calango

Este é um projeto de uma API utilizando Spring Boot 3.0, baseado na arquitetura de microserviços e com suporte a várias funcionalidades do Spring Framework, como injeção de dependência, segurança, JPA, entre outros.

## Estrutura do Projeto

A estrutura do projeto segue o padrão típico do Spring Boot:

- **controller**: Contém as classes que gerenciam as requisições HTTP.
- **model**: Contém as classes de modelo (entidades do JPA).
- **repository**: Contém os repositórios do Spring Data JPA.
- **service**: Contém a lógica de negócios.
- **application.properties**: Configurações da aplicação.:

## Objetivo

Esse projeto é uma API para ser consumida pelos demais sistemas da Covil do Calango, uma loja Geek de board e card games, o principal foco da API é auxiliar na geração de comandas, tendo em vista que a loja também trabalha com alimentação, servindo lanches e produtos para seus clientes.

Atualmente o sistema está sendo servido no [render](http://render.com/) e exposto por essa [url](https://covil-calango-api.onrender.com).

## Executando com Docker Compose

Na pasta do backend, construa e inicie a API e o PostgreSQL local:

```bash
docker compose up --build
```

A API ficará disponível em <http://localhost:8081>. Para executar em segundo plano, use `docker compose up --build -d`; para parar os serviços, use `docker compose down`. Os dados do PostgreSQL ficam no volume `postgres-data`.

O Compose publica o PostgreSQL somente em `localhost`, para acesso por ferramentas como o DBeaver. Use estes dados de conexão padrão:

- **Host:** `localhost`
- **Porta:** `5432`
- **Banco:** `covil_calango`
- **Usuário:** `covil_calango`
- **Senha:** `covil_calango`

Para personalizar as credenciais, defina `POSTGRES_DB`, `POSTGRES_USER` e `POSTGRES_PASSWORD` no ambiente ou em um arquivo `.env` na pasta do backend. Se a porta `5432` já estiver em uso, defina `DB_PORT` para outra porta disponível; use essa porta no DBeaver. A porta publicada pela API também pode ser alterada com `API_PORT` (padrão `8081`).
