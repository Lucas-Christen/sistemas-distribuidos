package br.edu.utfpr.sdcontas.servidor;

import br.edu.utfpr.sdcontas.protocolo.Dados;
import br.edu.utfpr.sdcontas.protocolo.Json;
import br.edu.utfpr.sdcontas.protocolo.JsonInvalidoException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Usuários em memória, gravados em um arquivo JSON a cada alteração.
 *
 * <p>Todos os métodos são {@code synchronized}: a checagem "username já existe?"
 * e a inserção acontecem juntas, então dois cadastros simultâneos do mesmo
 * username nunca passam. A gravação usa arquivo temporário + {@code move}
 * atômico, para o arquivo nunca ficar pela metade.
 */
public final class RepositorioUsuarios {

    private final Path arquivo;
    private final RegistroLog log;
    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();

    public RepositorioUsuarios(Path arquivo, RegistroLog log) {
        this.arquivo = arquivo.toAbsolutePath().normalize();
        this.log = log;
        carregar();
    }

    public synchronized Optional<Usuario> buscar(String username) {
        return Optional.ofNullable(username == null ? null : usuarios.get(username));
    }

    public synchronized boolean existe(String username) {
        return username != null && usuarios.containsKey(username);
    }

    /** Insere se o username ainda não existir. Retorna {@code false} se já existia. */
    public synchronized boolean inserir(Usuario usuario) {
        if (usuarios.containsKey(usuario.username())) {
            return false;
        }
        alterarESalvar(() -> usuarios.put(usuario.username(), usuario));
        return true;
    }

    public synchronized boolean atualizarNome(String username, String novoNome) {
        Usuario atual = usuarios.get(username);
        if (atual == null) {
            return false;
        }
        alterarESalvar(() -> usuarios.put(username, atual.comNome(novoNome)));
        return true;
    }

    public synchronized boolean atualizarSenha(String username, String novoHash) {
        Usuario atual = usuarios.get(username);
        if (atual == null) {
            return false;
        }
        alterarESalvar(() -> usuarios.put(username, atual.comSenha(novoHash)));
        return true;
    }

    public synchronized boolean remover(String username) {
        if (!usuarios.containsKey(username)) {
            return false;
        }
        alterarESalvar(() -> usuarios.remove(username));
        return true;
    }

    public synchronized List<Usuario> listar() {
        return new ArrayList<>(usuarios.values());
    }

    public Path getArquivo() {
        return arquivo;
    }

    /** Aplica a mudança e grava; se a gravação falhar, desfaz a mudança na memória. */
    private void alterarESalvar(Runnable mudanca) {
        Map<String, Usuario> copia = new LinkedHashMap<>(usuarios);
        mudanca.run();
        try {
            salvar();
        } catch (IOException e) {
            usuarios.clear();
            usuarios.putAll(copia);
            throw new UncheckedIOException("não foi possível gravar " + arquivo, e);
        }
    }

    private void salvar() throws IOException {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Usuario u : usuarios.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", u.name());
            item.put("username", u.username());
            item.put("password", u.password());
            lista.add(item);
        }
        Files.createDirectories(arquivo.getParent());
        Path temporario = Files.createTempFile(arquivo.getParent(), "usuarios-", ".tmp");
        try {
            Files.writeString(temporario, Json.escrever(lista) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException semMoveAtomico) {
                Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    private void carregar() {
        if (!Files.exists(arquivo)) {
            log.info("Arquivo de usuários ainda não existe; será criado em " + arquivo);
            return;
        }
        try {
            Object conteudo = Json.ler(Files.readString(arquivo, StandardCharsets.UTF_8));
            if (!(conteudo instanceof List<?> lista)) {
                throw new JsonInvalidoException("o arquivo deve conter uma lista", 0);
            }
            for (Object item : lista) {
                Map<String, Object> objeto = Dados.comoObjeto(item);
                String name = Dados.texto(objeto, "name");
                String username = Dados.texto(objeto, "username");
                String password = Dados.texto(objeto, "password");
                if (name != null && username != null && password != null) {
                    usuarios.put(username, new Usuario(name, username, password));
                }
            }
            log.info(usuarios.size() + " usuário(s) carregado(s) de " + arquivo);
        } catch (IOException | JsonInvalidoException e) {
            Path copia = arquivo.resolveSibling(arquivo.getFileName() + ".corrompido");
            log.erro("Arquivo de usuários ilegível (" + e.getMessage() + "). Movido para " + copia
                    + " e começando vazio.");
            try {
                Files.move(arquivo, copia, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException naoMoveu) {
                log.erro("Também não foi possível mover o arquivo: " + naoMoveu.getMessage());
            }
        }
    }
}
