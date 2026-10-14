# SD-Contas TCP

Cliente e servidor de **cadastro e sessão de usuários** sobre **sockets TCP com mensagens JSON**, seguindo o protocolo combinado na disciplina de Sistemas Distribuídos (UTFPR).

- Java **17+**, sem nenhuma dependência externa (nem Maven): compila com `javac` ou no Eclipse.
- Cliente e servidor com interface gráfica (Swing). O servidor também roda só no terminal.
- Feito para **conversar com as implementações dos colegas**: segue o protocolo à risca no que envia e é tolerante no que recebe.

---

## Como rodar

### Pelo terminal (Linux/macOS ou Windows)
```bash
./compilar.sh                            # Windows: compilar.bat
java -jar dist/servidor.jar              # janela do servidor (porta padrão 21500)
java -jar dist/servidor.jar --console    # servidor no terminal (pergunta a porta)
java -jar dist/servidor.jar --console 21500 --dados minha-pasta
java -jar dist/cliente.jar               # janela do cliente
./testar.sh                              # Windows: testar.bat — roda os 179 testes
```

### Pelo Eclipse
`File → Import → Existing Projects into Workspace` e escolha esta pasta. Depois, `Run As → Java Application` em:
- `br.edu.utfpr.sdcontas.servidor.ServidorApp`
- `br.edu.utfpr.sdcontas.cliente.ClienteApp`
- `br.edu.utfpr.sdcontas.TodosOsTestes` (testes)

### Testar na mão com netcat
```bash
echo '{"method":"login","data":{"username":"joao.s","password":"Senha123!"}}' | nc localhost 21500
```

Na janela do servidor aparecem os IPs da máquina (ex.: `192.168.0.10:21500`) para os colegas se conectarem pela rede.

---

## Protocolo (resumo)

Requisição: `{"method": "<minúsculo>", "data": {...}}` · Resposta: `{"statusCode": int, "message": String, "data": T|null}`.
Uma conexão TCP por requisição; o servidor responde uma linha JSON terminada em `\n` e encerra a conexão.

| method | data enviado | sucesso |
| :--- | :--- | :--- |
| `login` | `username`, `password` | `200 "Sucesso no Login"`, `data: {token}` |
| `register` | `name`, `username`, `password` | `201 "Usuário criado com sucesso"` |
| `logout` | `token` | `200 "Usuário deslogado com sucesso"` |
| `getuser` | `token`, `username` | `200 "Usuário encontrado com sucesso"`, `data: {name, username}` |
| `updateusername` | `token`, `username`, `name` | `200 "Nome do usuário atualizado com sucesso"` |
| `updateuserpassword` | `token`, `username`, `oldPassword`, `newPassword` | `200 "Senha atualizada com sucesso"` |
| `deleteuser` | `token`, `username` | `200 "Usuário deletado com sucesso"` |

Erros da especificação: `401 "Usuario ou senhas inválidos"`, `409 "O username já está em uso."`, `400 "O campo {campo} não está no padrão esperado."`, `400 "Token de autenticação não fornecido."`, `401 "Sessão expirada ou encerrada."`, `400 "Nome do usuário fora do padrão"`, `400 "Senha fora do padrão"`, `401 "Operação não autorizada"`, `404 "Usuário não encontrado"`.

Todas as mensagens estão centralizadas em `protocolo/Protocolo.java`.

### Decisões onde a especificação não define
| Situação | Resposta escolhida | Por quê |
| :--- | :--- | :--- |
| Token em formato não-UUID (`"abc"`, `123`) | `401 "Sessão expirada ou encerrada."` | A especificação diz "sempre que um token não autorizado chegar" |
| Token vazio (`""`) | `400 "Token de autenticação não fornecido."` | Vazio = não fornecido |
| `username` de outra pessoa (getuser/update/delete) | `401 "Operação não autorizada"` | `username` é o ID da operação (negrito na planilha); admin ainda não especificado |
| `username` ausente nessas operações | Vale o dono do token | Tolerância com clientes que não mandam |
| Senha atual errada | `401 "Senha atual incorreta"` | Mesma semântica do login (credencial errada) |
| Nova senha igual à atual | Aceita (200) | A especificação não proíbe |
| `method` desconhecido | `400 "Método desconhecido: X"` | Requisição inválida |
| `method` com maiúscula (`"Login"`) | Aceito | O cliente deveria mandar minúsculo, mas aceitar não custa nada |
| `name` com acento (`João`) | Aceito | Só o `username` proíbe acento |

---

## Como ele conversa com implementações diferentes

**O que envia segue a especificação à risca:** chaves e mensagens exatas, `data:null` sempre presente, JSON compacto de uma linha terminado em `\n`, UTF-8, token UUID.

**O que recebe é tolerante.** A leitura não depende do `\n`: ela acompanha as chaves do JSON e para quando o objeto fecha (`protocolo/Transporte.java`). Assim, funciona quando o outro lado:
- manda com `\n`, `\r\n`, sem `\n` (e espera), ou sem `\n` e fecha a conexão;
- manda JSON formatado em várias linhas;
- manda uma linha malformada: responde 400 em ~300 ms, em vez de esperar o tempo limite;
- usa **Windows-1252** em vez de UTF-8 (Java 17 no Windows faz isso por padrão): o servidor detecta e responde no mesmo charset;
- manda BOM, chaves com outra caixa (`Username`), `data` como texto com JSON dentro;
- (cliente) responde `statusCode` como `200`, `200.0` ou `"200"`, sem `message`, ou sem `data`.

**Sessão expirada no cliente:** num 401, o cliente pergunta ao servidor (`getuser`) se o token ainda vale antes de deslogar. Assim ele não depende do texto da mensagem: "Senha atual incorreta" (401) não derruba o usuário.

**Painel de tráfego:** o cliente mostra cada JSON enviado/recebido (senhas mascaradas). É útil para descobrir o que o servidor de um colega espera.

---

## Estrutura

```
src/br/edu/utfpr/sdcontas/
├── protocolo/   Json (parser próprio), Transporte (enquadramento TCP), Protocolo (constantes),
│                Resposta, Validacao, Dados (leitura tolerante), LogSeguro (mascara senhas)
├── servidor/    ServidorApp (main), ServidorTcp (accept + pool), Atendimento (1 conexão),
│                Despachante (method → operação), ServicoContas (regras), RepositorioUsuarios,
│                Sessoes, Senhas (PBKDF2), RegistroLog, ui/JanelaServidor
└── cliente/     ClienteApp (main), ClienteTcp (1 conexão por requisição), ApiContas (sessão),
                 ui/JanelaCliente
teste/br/edu/utfpr/sdcontas/   TodosOsTestes, TesteProtocolo, TesteServidor, Verifica
```

### Servidor
- **Thread de accept + pool com limite** (4 a 64 threads). Se lotar, responde 500 na hora.
- **Tempo limite de 30 s por conexão:** uma conexão aberta sem enviar nada não prende uma thread para sempre.
- **Senhas com hash PBKDF2-HMAC-SHA256 + sal** (só JDK). O arquivo `dados/usuarios.json` nunca guarda a senha.
- **Usuários em memória e gravados a cada alteração** (arquivo temporário + move atômico). Operações `synchronized`, então dois cadastros simultâneos do mesmo username não passam.
- **Sessões (token → username) só em memória:** reiniciar o servidor invalida os tokens. Excluir a conta derruba todas as sessões do usuário.
- **Log** no formato da especificação (`[SERVIDOR] Requisição recebida/Resposta enviada`, `[CLIENTE] Login/Logout`), com horário e senhas mascaradas.

---

## Testes

`./testar.sh` roda **179 verificações** sem JUnit:
- JSON (escapes, unicode, números, entradas inválidas) e regras de validação nos limites;
- todos os cenários da especificação contra o servidor real numa porta livre;
- robustez de rede (sem `\n`, multilinha, em pedaços, Windows-1252, BOM, conexão ociosa, JSON pela metade, mensagem > 64 KB);
- concorrência (40 cadastros simultâneos);
- persistência (reinício do servidor, senha não aparece no arquivo);
- cliente contra servidores "diferentes" (respostas formatadas, sem `\n`, `statusCode` como texto, servidor que fecha sem responder).
