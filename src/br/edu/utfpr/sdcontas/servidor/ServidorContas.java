package br.edu.utfpr.sdcontas.servidor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Monta o servidor completo (dados, sessões, regras, TCP) e expõe o que a janela precisa mostrar. */
public final class ServidorContas {

    private final RepositorioUsuarios repositorio;
    private final Sessoes sessoes;
    private final ServidorTcp tcp;

    public ServidorContas(int porta, Path arquivoUsuarios, RegistroLog log) {
        this(porta, arquivoUsuarios, log, ServidorTcp.TEMPO_LIMITE_PADRAO_MS);
    }

    public ServidorContas(int porta, Path arquivoUsuarios, RegistroLog log, int tempoLimiteMs) {
        this.repositorio = new RepositorioUsuarios(arquivoUsuarios, log);
        this.sessoes = new Sessoes();
        ServicoContas servico = new ServicoContas(repositorio, sessoes, log);
        this.tcp = new ServidorTcp(porta, new Despachante(servico, log), log, tempoLimiteMs);
    }

    public void iniciar() throws IOException {
        tcp.iniciar();
    }

    public void parar() {
        tcp.parar();
    }

    public boolean isRodando() {
        return tcp.isRodando();
    }

    public int getPorta() {
        return tcp.getPorta();
    }

    public List<Usuario> listarUsuarios() {
        return repositorio.listar();
    }

    public List<Sessoes.Sessao> listarSessoes() {
        return sessoes.listar();
    }

    public Path getArquivoUsuarios() {
        return repositorio.getArquivo();
    }
}
