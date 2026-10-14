package br.edu.utfpr.sdcontas;

import br.edu.utfpr.sdcontas.cliente.ApiContas;
import br.edu.utfpr.sdcontas.cliente.ClienteTcp;
import br.edu.utfpr.sdcontas.cliente.FalhaComunicacao;
import br.edu.utfpr.sdcontas.protocolo.Json;
import br.edu.utfpr.sdcontas.protocolo.Resposta;
import br.edu.utfpr.sdcontas.protocolo.Transporte;
import br.edu.utfpr.sdcontas.servidor.RegistroLog;
import br.edu.utfpr.sdcontas.servidor.ServidorContas;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testes de integração: sobe o servidor de verdade numa porta livre e conversa
 * com ele por socket, como um cliente qualquer da turma faria.
 */
final class TesteServidor {

    private static final String SENHA = "Senha123!";
    private static final int TEMPO_CURTO_MS = 1500;
    private static int porta;

    private TesteServidor() {
    }

    static void executar(Verifica v) throws Exception {
        Path pasta = Files.createTempDirectory("sdcontas-teste");
        Path arquivo = pasta.resolve("usuarios.json");
        ServidorContas servidor = new ServidorContas(0, arquivo, new RegistroLog(false), TEMPO_CURTO_MS);
        servidor.iniciar();
        porta = servidor.getPorta();
        try {
            especificacao(v);
            robustez(v);
            concorrencia(v);
            clienteContraServidor(v);
        } finally {
            servidor.parar();
        }
        persistencia(v, arquivo);
        clienteContraServidoresDiferentes(v);
    }

    // ------------------------------------------------------- especificação

    private static void especificacao(Verifica v) throws IOException {
        v.grupo("Especificação — register");
        esperar(v, "cadastro válido", "register", "{\"name\":\"Joao Silva\",\"username\":\"joao.s\",\"password\":\"" + SENHA + "\"}",
                201, "Usuário criado com sucesso");
        esperar(v, "username duplicado", "register", "{\"name\":\"Outro\",\"username\":\"joao.s\",\"password\":\"" + SENHA + "\"}",
                409, "O username já está em uso.");
        esperar(v, "name com número", "register", "{\"name\":\"Jo4o\",\"username\":\"novo1\",\"password\":\"" + SENHA + "\"}",
                400, "O campo name não está no padrão esperado.");
        esperar(v, "name com 61 letras", "register", "{\"name\":\"" + "a".repeat(61) + "\",\"username\":\"novo1\",\"password\":\"" + SENHA + "\"}",
                400, "O campo name não está no padrão esperado.");
        esperar(v, "username com maiúscula", "register", "{\"name\":\"Ana\",\"username\":\"Ana\",\"password\":\"" + SENHA + "\"}",
                400, "O campo username não está no padrão esperado.");
        esperar(v, "username com 2 letras", "register", "{\"name\":\"Ana\",\"username\":\"an\",\"password\":\"" + SENHA + "\"}",
                400, "O campo username não está no padrão esperado.");
        esperar(v, "senha sem símbolo", "register", "{\"name\":\"Ana\",\"username\":\"ana1\",\"password\":\"Senha1234\"}",
                400, "O campo password não está no padrão esperado.");
        esperar(v, "senha com '?'", "register", "{\"name\":\"Ana\",\"username\":\"ana1\",\"password\":\"Senha123?\"}",
                400, "O campo password não está no padrão esperado.");
        esperar(v, "senha com 21 caracteres", "register", "{\"name\":\"Ana\",\"username\":\"ana1\",\"password\":\"Aa1!" + "a".repeat(17) + "\"}",
                400, "O campo password não está no padrão esperado.");
        esperar(v, "sem password", "register", "{\"name\":\"Ana\",\"username\":\"ana2\"}",
                400, "O campo password não está no padrão esperado.");
        esperar(v, "sem username", "register", "{\"name\":\"Ana\",\"password\":\"" + SENHA + "\"}",
                400, "O campo username não está no padrão esperado.");

        v.grupo("Especificação — login");
        esperar(v, "senha errada", "login", "{\"username\":\"joao.s\",\"password\":\"Errada123!\"}",
                401, "Usuario ou senhas inválidos");
        esperar(v, "usuário inexistente", "login", "{\"username\":\"ninguem\",\"password\":\"" + SENHA + "\"}",
                401, "Usuario ou senhas inválidos");
        Map<String, Object> login = esperar(v, "login válido", "login",
                "{\"username\":\"joao.s\",\"password\":\"" + SENHA + "\"}", 200, "Sucesso no Login");
        String token = token(login);
        v.verdadeiro("token é UUID", ehUuid(token));

        v.grupo("Especificação — getuser");
        Map<String, Object> dados = esperar(v, "consulta válida", "getuser",
                "{\"token\":\"" + token + "\",\"username\":\"joao.s\"}", 200, "Usuário encontrado com sucesso");
        v.igual("data.name", "Joao Silva", dataCampo(dados, "name"));
        v.igual("data.username", "joao.s", dataCampo(dados, "username"));
        esperar(v, "token UUID desconhecido", "getuser",
                "{\"token\":\"" + UUID.randomUUID() + "\",\"username\":\"joao.s\"}", 401, "Sessão expirada ou encerrada.");
        esperar(v, "token que nem é UUID", "getuser", "{\"token\":\"abc\",\"username\":\"joao.s\"}",
                401, "Sessão expirada ou encerrada.");
        esperar(v, "token como número", "getuser", "{\"token\":123,\"username\":\"joao.s\"}",
                401, "Sessão expirada ou encerrada.");
        esperar(v, "token vazio", "getuser", "{\"token\":\"\",\"username\":\"joao.s\"}",
                400, "Token de autenticação não fornecido.");
        esperar(v, "sem token", "getuser", "{\"username\":\"joao.s\"}", 400, "Token de autenticação não fornecido.");
        esperar(v, "username de outra pessoa", "getuser", "{\"token\":\"" + token + "\",\"username\":\"outro\"}",
                401, "Operação não autorizada");
        esperar(v, "sem username (usa o dono do token)", "getuser", "{\"token\":\"" + token + "\"}",
                200, "Usuário encontrado com sucesso");

        v.grupo("Especificação — updateusername");
        esperar(v, "nome válido", "updateusername", "{\"token\":\"" + token + "\",\"username\":\"joao.s\",\"name\":\"Joao Pedro\"}",
                200, "Nome do usuário atualizado com sucesso");
        esperar(v, "nome inválido", "updateusername", "{\"token\":\"" + token + "\",\"username\":\"joao.s\",\"name\":\"J0ao!\"}",
                400, "Nome do usuário fora do padrão");
        esperar(v, "outro usuário", "updateusername", "{\"token\":\"" + token + "\",\"username\":\"outro\",\"name\":\"Hacker\"}",
                401, "Operação não autorizada");
        esperar(v, "token inválido", "updateusername", "{\"token\":\"x\",\"username\":\"joao.s\",\"name\":\"Ana\"}",
                401, "Sessão expirada ou encerrada.");

        v.grupo("Especificação — updateuserpassword");
        esperar(v, "senha atual errada", "updateuserpassword", senhaJson(token, "joao.s", "Errada123!", "Nova1234!"),
                401, "Senha atual incorreta");
        esperar(v, "nova senha fora do padrão", "updateuserpassword", senhaJson(token, "joao.s", SENHA, "fraca"),
                400, "Senha fora do padrão");
        esperar(v, "outro usuário", "updateuserpassword", senhaJson(token, "outro", SENHA, "Nova1234!"),
                401, "Operação não autorizada");
        esperar(v, "troca válida", "updateuserpassword", senhaJson(token, "joao.s", SENHA, "Nova1234!"),
                200, "Senha atualizada com sucesso");
        esperar(v, "login com a senha antiga falha", "login", "{\"username\":\"joao.s\",\"password\":\"" + SENHA + "\"}",
                401, "Usuario ou senhas inválidos");
        esperar(v, "login com a senha nova", "login", "{\"username\":\"joao.s\",\"password\":\"Nova1234!\"}",
                200, "Sucesso no Login");

        v.grupo("Especificação — logout");
        esperar(v, "data sem token", "logout", "{}", 400, "Token de autenticação não fornecido.");
        esperarBruto(v, "sem data", "{\"method\":\"logout\"}", 400, "Token de autenticação não fornecido.");
        esperarBruto(v, "data null", "{\"method\":\"logout\",\"data\":null}", 400, "Token de autenticação não fornecido.");
        esperar(v, "logout válido", "logout", "{\"token\":\"" + token + "\"}", 200, "Usuário deslogado com sucesso");
        esperar(v, "logout repetido", "logout", "{\"token\":\"" + token + "\"}", 401, "Sessão expirada ou encerrada.");
        esperar(v, "getuser depois do logout", "getuser", "{\"token\":\"" + token + "\",\"username\":\"joao.s\"}",
                401, "Sessão expirada ou encerrada.");

        v.grupo("Especificação — deleteuser");
        String t1 = token(requisitar("login", "{\"username\":\"joao.s\",\"password\":\"Nova1234!\"}"));
        String t2 = token(requisitar("login", "{\"username\":\"joao.s\",\"password\":\"Nova1234!\"}"));
        esperar(v, "outro usuário", "deleteuser", "{\"token\":\"" + t1 + "\",\"username\":\"outro\"}",
                401, "Operação não autorizada");
        esperar(v, "exclusão válida", "deleteuser", "{\"token\":\"" + t1 + "\",\"username\":\"joao.s\"}",
                200, "Usuário deletado com sucesso");
        esperar(v, "a outra sessão do mesmo usuário também cai", "getuser",
                "{\"token\":\"" + t2 + "\",\"username\":\"joao.s\"}", 401, "Sessão expirada ou encerrada.");
        esperar(v, "não loga mais", "login", "{\"username\":\"joao.s\",\"password\":\"Nova1234!\"}",
                401, "Usuario ou senhas inválidos");

        v.grupo("Envelope da requisição");
        esperarBruto(v, "sem method", "{\"data\":{}}", 400, "O campo 'method' é obrigatório.");
        esperarBruto(v, "method desconhecido", "{\"method\":\"foo\",\"data\":{}}", 400, "Método desconhecido: foo");
        esperarBruto(v, "JSON malformado", "{\"method\": \"login\", ", 400, "JSON malformado");
        esperarBruto(v, "linha vazia", "", 400, "JSON malformado");
        esperarBruto(v, "array em vez de objeto", "[1,2]", 400, "A requisição deve ser um objeto JSON.");
        esperarBruto(v, "data como texto comum", "{\"method\":\"login\",\"data\":\"abc\"}",
                400, "O campo 'data' deve ser um objeto JSON.");
        esperarBruto(v, "login sem data", "{\"method\":\"login\"}", 401, "Usuario ou senhas inválidos");
    }

    // ---------------------------------------------------- tolerância e rede

    private static void robustez(Verifica v) throws Exception {
        requisitar("register", "{\"name\":\"Maria\",\"username\":\"maria\",\"password\":\"" + SENHA + "\"}");
        String login = "{\"method\":\"login\",\"data\":{\"username\":\"maria\",\"password\":\"" + SENHA + "\"}}";

        v.grupo("Tolerância com clientes de outras pessoas");
        v.igual("method com maiúscula (\"Login\")", 200L, status(trocar(
                "{\"method\":\"Login\",\"data\":{\"username\":\"maria\",\"password\":\"" + SENHA + "\"}}\n")));
        v.igual("terminado em \\r\\n (Windows)", 200L, status(trocar(login + "\r\n")));
        v.igual("JSON em várias linhas", 200L, status(trocar(
                "{\n  \"method\": \"login\",\n  \"data\": {\n    \"username\": \"maria\",\n    \"password\": \"" + SENHA + "\"\n  }\n}\n")));
        v.igual("JSON em várias linhas enviado aos poucos (pausas de 100 ms)", 200L, status(trocarEmPedacos(
                "{\n", "  \"method\": \"login\",\n", "  \"data\": {\"username\": \"maria\", \"password\": \"" + SENHA + "\"}\n", "}\n")));
        long inicio = System.nanoTime();
        long malformado = status(trocar("{\"method\": \"login\", \n"));
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        v.verdadeiro("linha malformada (chave sem fechar) → 400 sem esperar o tempo limite (" + ms + " ms)",
                malformado == 400 && ms < 1000);
        inicio = System.nanoTime();
        malformado = status(trocar("{\"method\": \"login\", \"data\": {\"username\": \"maria\n"));
        ms = (System.nanoTime() - inicio) / 1_000_000;
        v.verdadeiro("linha com aspas sem fechar → 400 sem esperar (" + ms + " ms)", malformado == 400 && ms < 1000);
        v.igual("sem \\n, cliente esperando com a conexão aberta", 200L, status(trocar(login)));
        v.igual("sem \\n, cliente fecha a escrita", 200L, status(trocarFechandoEscrita(login)));
        v.igual("com BOM do UTF-8 no início", 200L, status(trocarBytes(concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                (login + "\n").getBytes(StandardCharsets.UTF_8)))));
        v.igual("data mandado como texto com JSON dentro", 200L, status(trocar(
                "{\"method\":\"login\",\"data\":\"{\\\"username\\\":\\\"maria\\\",\\\"password\\\":\\\"" + SENHA + "\\\"}\"}\n")));
        v.igual("chaves com outra caixa (Username/PASSWORD)", 200L, status(trocar(
                "{\"method\":\"login\",\"data\":{\"Username\":\"maria\",\"PASSWORD\":\"" + SENHA + "\"}}\n")));

        byte[] emWindows = ("{\"method\":\"register\",\"data\":{\"name\":\"João Ação\",\"username\":\"joao.win\",\"password\":\""
                + SENHA + "\"}}\n").getBytes(Transporte.WINDOWS_1252);
        byte[] bruto = trocarBytesCru(emWindows);
        String respostaWindows = new String(bruto, Transporte.WINDOWS_1252).strip();
        v.igual("requisição em Windows-1252 é entendida", 201L, status(respostaWindows));
        v.verdadeiro("e a resposta volta em Windows-1252 (\"Usuário\" legível)", respostaWindows.contains("Usuário criado"));
        String tw = token(requisitar("login", "{\"username\":\"joao.win\",\"password\":\"" + SENHA + "\"}"));
        v.igual("nome com acento gravado certo", "João Ação",
                dataCampo(requisitar("getuser", "{\"token\":\"" + tw + "\",\"username\":\"joao.win\"}"), "name"));

        v.grupo("Conexão");
        try (Socket s = new Socket("127.0.0.1", porta)) {
            s.setSoTimeout(5000);
            s.getOutputStream().write((login + "\n").getBytes(StandardCharsets.UTF_8));
            InputStream in = s.getInputStream();
            ByteArrayOutputStream lido = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                lido.write(b);
            }
            v.verdadeiro("resposta termina com \\n (para quem usa readLine)", lido.toString(StandardCharsets.UTF_8).endsWith("}\n"));
            v.verdadeiro("servidor encerra a conexão depois de responder", true);
        }
        try (Socket ocioso = new Socket("127.0.0.1", porta)) {
            ocioso.setSoTimeout(5000);
            long inicioOcioso = System.nanoTime();
            int lido = ocioso.getInputStream().read();
            long msOcioso = (System.nanoTime() - inicioOcioso) / 1_000_000;
            v.verdadeiro("conexão ociosa é fechada pelo servidor (" + msOcioso + " ms, limite " + TEMPO_CURTO_MS + ")",
                    lido == -1 && msOcioso < 4000);
        } catch (SocketTimeoutException e) {
            v.verdadeiro("conexão ociosa é fechada pelo servidor", false);
        }
        try (Socket parcial = new Socket("127.0.0.1", porta)) {
            parcial.setSoTimeout(5000);
            parcial.getOutputStream().write("{\"method\":\"login\",".getBytes(StandardCharsets.UTF_8));
            v.igual("JSON pela metade e silêncio → 400 após o tempo limite", 400L, status(lerTudo(parcial.getInputStream())));
        }
        String gigante = "{\"method\":\"login\",\"data\":{\"username\":\"" + "a".repeat(70_000) + "\"}}\n";
        Map<String, Object> grande = comoMapa(trocar(gigante));
        v.igual("mensagem acima de 64 KB → 400", 400L, grande.get("statusCode"));
    }

    private static void concorrencia(Verifica v) throws Exception {
        v.grupo("Concorrência");
        AtomicInteger criados = new AtomicInteger();
        AtomicInteger conflitos = new AtomicInteger();
        rodarJuntos(40, i -> {
            long s = status(trocar("{\"method\":\"register\",\"data\":{\"name\":\"Corrida\",\"username\":\"corrida\",\"password\":\""
                    + SENHA + "\"}}\n"));
            (s == 201 ? criados : conflitos).incrementAndGet();
        });
        v.igual("40 cadastros simultâneos do mesmo username: 1 criado", 1, criados.get());
        v.igual("… e 39 conflitos", 39, conflitos.get());

        AtomicInteger distintos = new AtomicInteger();
        rodarJuntos(40, i -> {
            if (status(trocar("{\"method\":\"register\",\"data\":{\"name\":\"Usuario\",\"username\":\"user" + i
                    + "\",\"password\":\"" + SENHA + "\"}}\n")) == 201) {
                distintos.incrementAndGet();
            }
        });
        v.igual("40 cadastros simultâneos distintos: nenhum perdido", 40, distintos.get());
    }

    private static void clienteContraServidor(Verifica v) throws Exception {
        v.grupo("Cliente (ApiContas) contra o servidor");
        ApiContas api = new ApiContas(new ClienteTcp("127.0.0.1", porta));
        v.igual("cadastro", 201, api.cadastrar("Carla Souza", "carla", SENHA).statusCode());
        v.igual("login", 200, api.login("carla", SENHA).statusCode());
        v.verdadeiro("token guardado", api.isLogado());
        Resposta dados = api.consultar();
        v.igual("getuser traz o nome", "Carla Souza", dados.textoEmData("name"));
        v.igual("updateusername", 200, api.atualizarNome("Carla S").statusCode());
        Resposta senhaErrada = api.atualizarSenha("Errada123!", "Nova1234!");
        v.igual("senha atual errada → 401", 401, senhaErrada.statusCode());
        v.verdadeiro("… mas a sessão continua válida (cliente não desloga)", api.sessaoContinuaValida());
        v.igual("updateuserpassword", 200, api.atualizarSenha(SENHA, "Nova1234!").statusCode());
        String tokenAntigo = api.getToken();
        v.igual("logout", 200, api.logout().statusCode());
        v.falso("token apagado no cliente", api.isLogado());
        Map<String, Object> velho = requisitar("getuser", "{\"token\":\"" + tokenAntigo + "\"}");
        v.igual("token antigo não vale mais", 401L, velho.get("statusCode"));
        v.igual("login com senha nova", 200, api.login("carla", "Nova1234!").statusCode());
        v.igual("deleteuser", 200, api.excluirConta().statusCode());
        v.falso("sessão local encerrada após excluir", api.isLogado());
    }

    private static void persistencia(Verifica v, Path arquivo) throws Exception {
        v.grupo("Persistência");
        String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        v.falso("arquivo não guarda senha em texto puro", conteudo.contains(SENHA));
        v.verdadeiro("arquivo guarda hash PBKDF2", conteudo.contains("pbkdf2-sha256$"));
        ServidorContas outro = new ServidorContas(0, arquivo, new RegistroLog(false));
        outro.iniciar();
        try {
            ApiContas api = new ApiContas(new ClienteTcp("127.0.0.1", outro.getPorta()));
            v.igual("usuário sobrevive ao reinício", 200, api.login("maria", SENHA).statusCode());
            v.igual("acento sobrevive ao reinício", "João Ação", nomeDe(outro.getPorta(), "joao.win"));
        } finally {
            outro.parar();
        }
    }

    /** O cliente contra servidores "esquisitos" (respostas formatadas, sem \n, em Windows-1252...). */
    private static void clienteContraServidoresDiferentes(Verifica v) throws Exception {
        v.grupo("Cliente contra servidores com outros jeitos de responder");
        v.igual("resposta formatada em várias linhas, sem \\n no fim, e fecha",
                200, servidorFalso("{\n  \"statusCode\" : 200,\n  \"message\" : \"ok\",\n  \"data\" : null\n}", true));
        v.igual("resposta sem \\n e sem fechar a conexão", 200, servidorFalso("{\"statusCode\":200,\"message\":\"ok\"}", false));
        v.igual("statusCode como texto", 401, servidorFalso("{\"statusCode\":\"401\",\"message\":\"x\"}\n", true));
        v.lanca("servidor que fecha sem responder vira FalhaComunicacao", FalhaComunicacao.class,
                () -> servidorFalso("", true));
        v.lanca("servidor que responde texto que não é JSON vira FalhaComunicacao", FalhaComunicacao.class,
                () -> servidorFalso("OK\n", true));
        v.lanca("porta fechada vira FalhaComunicacao", FalhaComunicacao.class, () -> {
            int livre;
            try (ServerSocket s = new ServerSocket(0)) {
                livre = s.getLocalPort();
            }
            new ClienteTcp("127.0.0.1", livre).enviar("getuser", Map.of());
        });
    }

    // ------------------------------------------------------------- utilidades

    private static Map<String, Object> esperar(Verifica v, String descricao, String metodo, String dataJson,
                                               int status, String mensagem) throws IOException {
        return esperarBruto(v, descricao, "{\"method\":\"" + metodo + "\",\"data\":" + dataJson + "}", status, mensagem);
    }

    private static Map<String, Object> esperarBruto(Verifica v, String descricao, String requisicao,
                                                    int status, String mensagem) throws IOException {
        String texto = trocar(requisicao + "\n");
        Map<String, Object> resposta = comoMapa(texto);
        boolean ok = Long.valueOf(status).equals(resposta.get("statusCode"))
                && mensagem.equals(resposta.get("message")) && resposta.containsKey("data");
        v.verdadeiro(descricao + " → " + status + " \"" + mensagem + "\"" + (ok ? "" : "   recebido: " + texto), ok);
        return resposta;
    }

    private static Map<String, Object> requisitar(String metodo, String dataJson) throws IOException {
        return comoMapa(trocar("{\"method\":\"" + metodo + "\",\"data\":" + dataJson + "}\n"));
    }

    private static String trocar(String envio) throws IOException {
        return new String(trocarBytesCru(envio.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8).strip();
    }

    private static String trocarBytes(byte[] envio) throws IOException {
        return new String(trocarBytesCru(envio), StandardCharsets.UTF_8).strip();
    }

    /** Envia os bytes sem fechar a escrita e lê até o servidor fechar a conexão. */
    private static byte[] trocarBytesCru(byte[] envio) throws IOException {
        try (Socket s = new Socket("127.0.0.1", porta)) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            out.write(envio);
            out.flush();
            return lerTudoBytes(s.getInputStream());
        }
    }

    private static String trocarEmPedacos(String... pedacos) throws Exception {
        try (Socket s = new Socket("127.0.0.1", porta)) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            for (String pedaco : pedacos) {
                out.write(pedaco.getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(100);
            }
            return lerTudo(s.getInputStream());
        }
    }

    private static String trocarFechandoEscrita(String envio) throws IOException {
        try (Socket s = new Socket("127.0.0.1", porta)) {
            s.setSoTimeout(5000);
            s.getOutputStream().write(envio.getBytes(StandardCharsets.UTF_8));
            s.shutdownOutput();
            return lerTudo(s.getInputStream());
        }
    }

    private static String lerTudo(InputStream in) throws IOException {
        return new String(lerTudoBytes(in), StandardCharsets.UTF_8).strip();
    }

    private static byte[] lerTudoBytes(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            b.write(buf, 0, n);
        }
        return b.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> comoMapa(String json) {
        return (Map<String, Object>) Json.ler(json);
    }

    private static long status(String json) {
        return (Long) comoMapa(json).get("statusCode");
    }

    @SuppressWarnings("unchecked")
    private static String token(Map<String, Object> resposta) {
        return (String) ((Map<String, Object>) resposta.get("data")).get("token");
    }

    @SuppressWarnings("unchecked")
    private static Object dataCampo(Map<String, Object> resposta, String campo) {
        Object data = resposta.get("data");
        return data instanceof Map ? ((Map<String, Object>) data).get(campo) : null;
    }

    private static String nomeDe(int portaServidor, String username) throws FalhaComunicacao {
        ApiContas api = new ApiContas(new ClienteTcp("127.0.0.1", portaServidor));
        api.login(username, SENHA);
        return api.consultar().textoEmData("name");
    }

    private static String senhaJson(String token, String username, String atual, String nova) {
        return "{\"token\":\"" + token + "\",\"username\":\"" + username + "\",\"oldPassword\":\"" + atual
                + "\",\"newPassword\":\"" + nova + "\"}";
    }

    private static boolean ehUuid(String texto) {
        try {
            return UUID.fromString(texto).toString().equals(texto);
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private interface Tarefa {
        void rodar(int i) throws Exception;
    }

    private static void rodarJuntos(int quantidade, Tarefa tarefa) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(quantidade);
        CountDownLatch largada = new CountDownLatch(1);
        Map<Integer, Throwable> erros = new ConcurrentHashMap<>();
        for (int i = 0; i < quantidade; i++) {
            int n = i;
            pool.execute(() -> {
                try {
                    largada.await();
                    tarefa.rodar(n);
                } catch (Throwable t) {
                    erros.put(n, t);
                }
            });
        }
        largada.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        if (!erros.isEmpty()) {
            throw new IllegalStateException("falhas nas threads: " + erros);
        }
    }

    /** Servidor de uma conexão só, que responde exatamente o texto dado. */
    private static int servidorFalso(String resposta, boolean fecharDepois) throws Exception {
        try (ServerSocket falso = new ServerSocket(0)) {
            Thread t = new Thread(() -> {
                try (Socket s = falso.accept()) {
                    s.setSoTimeout(5000);
                    Transporte.lerMensagem(s.getInputStream(), null, 0);
                    s.getOutputStream().write(resposta.getBytes(StandardCharsets.UTF_8));
                    s.getOutputStream().flush();
                    if (!fecharDepois) {
                        Thread.sleep(3000); // segura a conexão aberta: o cliente não pode depender do fechamento
                    }
                } catch (Exception ignorada) {
                    // fim do servidor falso
                }
            });
            t.setDaemon(true);
            t.start();
            return new ClienteTcp("127.0.0.1", falso.getLocalPort()).enviar("getuser", Map.of()).statusCode();
        }
    }
}
