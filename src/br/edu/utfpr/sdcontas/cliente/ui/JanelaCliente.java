package br.edu.utfpr.sdcontas.cliente.ui;

import static br.edu.utfpr.sdcontas.protocolo.Protocolo.*;

import br.edu.utfpr.sdcontas.cliente.ApiContas;
import br.edu.utfpr.sdcontas.cliente.ClienteTcp;
import br.edu.utfpr.sdcontas.cliente.FalhaComunicacao;
import br.edu.utfpr.sdcontas.protocolo.Resposta;
import br.edu.utfpr.sdcontas.protocolo.Validacao;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.text.DefaultCaret;

/**
 * Janela do cliente. Duas telas (CardLayout): "acesso" (servidor, login e cadastro)
 * e "conta" (dados, alterar nome/senha, sair, excluir). Embaixo fica o painel de
 * tráfego com cada JSON enviado e recebido, útil para testar com servidores de colegas.
 */
public final class JanelaCliente extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color VERDE = new Color(0, 120, 0);
    private static final Color VERMELHO = new Color(170, 0, 0);
    private static final String TELA_ACESSO = "acesso";
    private static final String TELA_CONTA = "conta";

    private final transient Preferences preferencias = Preferences.userNodeForPackage(JanelaCliente.class);
    private final transient ApiContas api;

    private final CardLayout telas = new CardLayout();
    private final JPanel painelTelas = new JPanel(telas);
    private final transient List<JComponent> acoes = new ArrayList<>();

    // servidor
    private final JTextField campoHost = new JTextField(14);
    private final JSpinner campoPorta = new JSpinner(new SpinnerNumberModel(PORTA_PADRAO, 1, 65535, 1));
    private final JCheckBox validarAntes = new JCheckBox("Validar campos antes de enviar", true);

    // acesso
    private final JTabbedPane abasAcesso = new JTabbedPane();
    private final JTextField loginUsername = new JTextField(18);
    private final JPasswordField loginSenha = new JPasswordField(18);
    private final JTextField cadNome = new JTextField(18);
    private final JTextField cadUsername = new JTextField(18);
    private final JPasswordField cadSenha = new JPasswordField(18);
    private final JPasswordField cadConfirma = new JPasswordField(18);

    // conta
    private final JLabel tituloConta = new JLabel();
    private final JLabel valorNome = new JLabel("—");
    private final JLabel valorUsername = new JLabel("—");
    private final JTextField novoNome = new JTextField(18);
    private final JPasswordField senhaAtual = new JPasswordField(18);
    private final JPasswordField senhaNova = new JPasswordField(18);
    private final JPasswordField senhaConfirma = new JPasswordField(18);

    // rodapé
    private final JTextArea trafego = new JTextArea(8, 60);
    private final JLabel status = new JLabel(" ");

    public JanelaCliente() {
        super("SD-Contas · Cliente");
        campoHost.setText(preferencias.get("host", "127.0.0.1"));
        campoPorta.setValue(preferencias.getInt("porta", PORTA_PADRAO));
        campoPorta.setEditor(new JSpinner.NumberEditor(campoPorta, "#"));

        api = new ApiContas(new ClienteTcp(campoHost.getText(), (Integer) campoPorta.getValue()));
        api.getTcp().setObservador(linha -> SwingUtilities.invokeLater(() -> registrarTrafego(linha)));

        painelTelas.add(montarTelaAcesso(), TELA_ACESSO);
        painelTelas.add(montarTelaConta(), TELA_CONTA);

        trafego.setEditable(false);
        trafego.setLineWrap(true);
        trafego.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ((DefaultCaret) trafego.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JButton limpar = new JButton("Limpar");
        limpar.addActionListener(e -> trafego.setText(""));
        JPanel painelTrafego = new JPanel(new BorderLayout());
        painelTrafego.setBorder(BorderFactory.createTitledBorder("Tráfego (JSON enviado → e recebido ←)"));
        painelTrafego.add(new JScrollPane(trafego), BorderLayout.CENTER);
        JPanel barraTrafego = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        barraTrafego.add(limpar);
        painelTrafego.add(barraTrafego, BorderLayout.SOUTH);

        JSplitPane divisao = new JSplitPane(JSplitPane.VERTICAL_SPLIT, painelTelas, painelTrafego);
        divisao.setResizeWeight(0.7);
        divisao.setDividerLocation(450);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(divisao, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(920, 740);
        setLocationRelativeTo(null);
        telas.show(painelTelas, TELA_ACESSO);
    }

    // =============================================================== telas

    private JPanel montarTelaAcesso() {
        JPanel servidor = formulario("Servidor",
                new String[] {"IP / host:", "Porta:"}, new JComponent[] {campoHost, campoPorta});
        adicionarLinha(servidor, validarAntes);
        servidor.setMaximumSize(new Dimension(Integer.MAX_VALUE, servidor.getPreferredSize().height));

        JButton entrar = acao(new JButton("Entrar"));
        entrar.addActionListener(e -> entrar());
        loginSenha.addActionListener(e -> entrar());
        JPanel abaLogin = formulario(null, new String[] {"Username:", "Senha:"},
                new JComponent[] {loginUsername, loginSenha});
        adicionarLinha(abaLogin, entrar);

        JButton cadastrar = acao(new JButton("Criar conta"));
        cadastrar.addActionListener(e -> cadastrar());
        JPanel abaCadastro = formulario(null,
                new String[] {"Nome (name):", "Username:", "Senha:", "Confirmar senha:"},
                new JComponent[] {cadNome, cadUsername, cadSenha, cadConfirma});
        adicionarLinha(abaCadastro, cadastrar);
        adicionarLinha(abaCadastro, dica(Validacao.REGRA_NOME, Validacao.REGRA_USERNAME, Validacao.REGRA_SENHA));

        abasAcesso.addTab("Entrar", noTopo(abaLogin));
        abasAcesso.addTab("Criar conta", noTopo(abaCadastro));

        JPanel tela = new JPanel();
        tela.setLayout(new BoxLayout(tela, BoxLayout.Y_AXIS));
        tela.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tela.add(servidor);
        tela.add(abasAcesso);
        return tela;
    }

    private JPanel montarTelaConta() {
        tituloConta.setFont(tituloConta.getFont().deriveFont(Font.BOLD, 15f));

        JButton recarregar = acao(new JButton("Recarregar (getuser)"));
        recarregar.addActionListener(e -> carregarDados());
        JPanel dados = formulario("Meus dados", new String[] {"Nome:", "Username:"},
                new JComponent[] {valorNome, valorUsername});
        adicionarLinha(dados, recarregar);

        JButton salvarNome = acao(new JButton("Salvar nome"));
        salvarNome.addActionListener(e -> alterarNome());
        novoNome.addActionListener(e -> alterarNome());
        JPanel nome = formulario("Alterar nome", new String[] {"Novo nome:"}, new JComponent[] {novoNome});
        adicionarLinha(nome, salvarNome);

        JButton salvarSenha = acao(new JButton("Alterar senha"));
        salvarSenha.addActionListener(e -> alterarSenha());
        JPanel senha = formulario("Alterar senha",
                new String[] {"Senha atual:", "Nova senha:", "Confirmar nova:"},
                new JComponent[] {senhaAtual, senhaNova, senhaConfirma});
        adicionarLinha(senha, salvarSenha);

        JButton sair = acao(new JButton("Sair (logout)"));
        sair.addActionListener(e -> sair());
        JButton excluir = acao(new JButton("Excluir minha conta"));
        excluir.setForeground(VERMELHO);
        excluir.addActionListener(e -> excluirConta());
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botoes.add(sair);
        botoes.add(excluir);

        JPanel colunas = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0;
        c.gridy = 0;
        colunas.add(dados, c);
        c.gridx = 1;
        colunas.add(nome, c);
        c.gridx = 0;
        c.gridy = 1;
        colunas.add(senha, c);
        c.gridx = 1;
        colunas.add(botoes, c);

        JPanel conteudo = new JPanel(new BorderLayout(0, 8));
        conteudo.add(tituloConta, BorderLayout.NORTH);
        conteudo.add(colunas, BorderLayout.CENTER);
        JPanel tela = new JPanel(new BorderLayout());
        tela.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tela.add(conteudo, BorderLayout.NORTH); // mantém tudo encostado no topo
        return tela;
    }

    // ============================================================= ações

    private void entrar() {
        String username = loginUsername.getText().strip();
        String senha = new String(loginSenha.getPassword());
        if (username.isEmpty() || senha.isEmpty()) {
            aviso("Informe username e senha.");
            return;
        }
        aplicarServidor();
        executar(() -> api.login(username, senha), r -> {
            if (r.statusCode() == OK) {
                loginSenha.setText("");
                abrirConta();
                mostrar(r);
                carregarDados();
            } else {
                mostrar(r);
            }
        });
    }

    private void cadastrar() {
        String nome = cadNome.getText();
        String username = cadUsername.getText().strip();
        String senha = new String(cadSenha.getPassword());
        if (!senha.equals(new String(cadConfirma.getPassword()))) {
            aviso("A senha e a confirmação não são iguais.");
            return;
        }
        if (validarAntes.isSelected()) {
            if (!Validacao.nomeValido(nome)) {
                aviso(Validacao.REGRA_NOME);
                return;
            }
            if (!Validacao.usernameValido(username)) {
                aviso(Validacao.REGRA_USERNAME);
                return;
            }
            if (!Validacao.senhaValida(senha)) {
                aviso(Validacao.REGRA_SENHA);
                return;
            }
        }
        aplicarServidor();
        executar(() -> api.cadastrar(nome, username, senha), r -> {
            mostrar(r);
            if (r.sucesso()) {
                cadSenha.setText("");
                cadConfirma.setText("");
                loginUsername.setText(username);
                abasAcesso.setSelectedIndex(0);
                loginSenha.requestFocusInWindow();
            }
        });
    }

    private void carregarDados() {
        executar(api::consultar, r -> {
            if (r.statusCode() == OK) {
                String nome = r.textoEmData("name");
                String username = r.textoEmData("username");
                valorNome.setText(nome == null ? "(não informado)" : nome);
                valorUsername.setText(username == null ? api.getUsername() : username);
                if (nome != null && novoNome.getText().isBlank()) {
                    novoNome.setText(nome);
                }
                mostrar(r);
            } else if (r.statusCode() == NAO_AUTORIZADO) {
                sessaoEncerrada(r); // no getuser, 401 só pode ser token inválido
            } else {
                mostrar(r);
            }
        });
    }

    private void alterarNome() {
        String nome = novoNome.getText();
        if (validarAntes.isSelected() && !Validacao.nomeValido(nome)) {
            aviso(Validacao.REGRA_NOME);
            return;
        }
        executar(() -> api.atualizarNome(nome), r -> {
            if (r.statusCode() == OK) {
                mostrar(r);
                carregarDados();
            } else {
                tratarErro(r);
            }
        });
    }

    private void alterarSenha() {
        String atual = new String(senhaAtual.getPassword());
        String nova = new String(senhaNova.getPassword());
        if (atual.isEmpty()) {
            aviso("Informe a senha atual.");
            return;
        }
        if (!nova.equals(new String(senhaConfirma.getPassword()))) {
            aviso("A nova senha e a confirmação não são iguais.");
            return;
        }
        if (validarAntes.isSelected() && !Validacao.senhaValida(nova)) {
            aviso(Validacao.REGRA_SENHA);
            return;
        }
        executar(() -> api.atualizarSenha(atual, nova), r -> {
            if (r.statusCode() == OK) {
                senhaAtual.setText("");
                senhaNova.setText("");
                senhaConfirma.setText("");
                mostrar(r);
            } else {
                tratarErro(r);
            }
        });
    }

    private void sair() {
        executar(api::logout, r -> {
            abrirAcesso();
            mostrar(r);
        }, falha -> {
            abrirAcesso();
            aviso("O servidor não confirmou o logout (" + falha.getMessage() + "). A sessão foi encerrada neste cliente.");
        });
    }

    private void excluirConta() {
        String username = api.getUsername();
        String digitado = JOptionPane.showInputDialog(this,
                "Isto apaga sua conta no servidor e não tem volta.\nDigite seu username (" + username
                        + ") para confirmar:", "Excluir conta", JOptionPane.WARNING_MESSAGE);
        if (digitado == null) {
            return;
        }
        if (!digitado.strip().equals(username)) {
            aviso("O username digitado não confere. Nada foi excluído.");
            return;
        }
        executar(api::excluirConta, r -> {
            if (r.statusCode() == OK) {
                abrirAcesso();
                mostrar(r);
            } else {
                tratarErro(r);
            }
        });
    }

    /**
     * Erro numa operação autenticada. Se for 401, pergunta ao servidor (getuser) se o
     * token ainda vale antes de deslogar: "Senha atual incorreta" também pode vir como 401.
     */
    private void tratarErro(Resposta r) {
        if (r.statusCode() != NAO_AUTORIZADO) {
            mostrar(r);
            return;
        }
        executar(() -> {
            boolean valida = api.sessaoContinuaValida();
            return valida ? r : null;
        }, verificada -> {
            if (verificada == null) {
                sessaoEncerrada(r);
            } else {
                mostrar(r);
            }
        });
    }

    private void sessaoEncerrada(Resposta r) {
        api.encerrarSessaoLocal();
        abrirAcesso();
        mostrarStatus("Sessão encerrada pelo servidor: " + r.statusCode() + " · " + r.message()
                + " Faça login novamente.", VERMELHO);
    }

    // ========================================================== navegação

    private void abrirConta() {
        tituloConta.setText("Logado como " + api.getUsername() + " em "
                + api.getTcp().getHost() + ":" + api.getTcp().getPorta());
        valorUsername.setText(api.getUsername());
        valorNome.setText("…");
        novoNome.setText("");
        telas.show(painelTelas, TELA_CONTA);
    }

    private void abrirAcesso() {
        senhaAtual.setText("");
        senhaNova.setText("");
        senhaConfirma.setText("");
        abasAcesso.setSelectedIndex(0);
        telas.show(painelTelas, TELA_ACESSO);
    }

    private void aplicarServidor() {
        String host = campoHost.getText().strip();
        int porta = (Integer) campoPorta.getValue();
        api.getTcp().configurar(host.isEmpty() ? "127.0.0.1" : host, porta);
        try {
            preferencias.put("host", host);
            preferencias.putInt("porta", porta);
        } catch (RuntimeException semPreferencias) {
            // sem permissão para gravar preferências: só não lembra na próxima vez
        }
    }

    // ===================================================== execução em 2º plano

    private void executar(Callable<Resposta> chamada, Consumer<Resposta> depois) {
        executar(chamada, depois, falha -> mostrarStatus(falha.getMessage(), VERMELHO));
    }

    /** Roda a chamada de rede fora da thread da interface e trava os botões enquanto isso. */
    private void executar(Callable<Resposta> chamada, Consumer<Resposta> depois,
                          Consumer<FalhaComunicacao> casoFalhe) {
        ocupado(true);
        new SwingWorker<Resposta, Void>() {
            @Override
            protected Resposta doInBackground() throws Exception {
                return chamada.call();
            }

            @Override
            protected void done() {
                ocupado(false);
                try {
                    depois.accept(get());
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof FalhaComunicacao falha) {
                        casoFalhe.accept(falha);
                    } else {
                        mostrarStatus("Erro inesperado: " + e.getCause(), VERMELHO);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void ocupado(boolean sim) {
        for (JComponent c : acoes) {
            c.setEnabled(!sim);
        }
        setCursor(sim ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    // ============================================================ mensagens

    private void mostrar(Resposta r) {
        mostrarStatus(r.statusCode() + " · " + r.message(), r.sucesso() ? VERDE : VERMELHO);
    }

    private void aviso(String texto) {
        mostrarStatus(texto, new Color(150, 90, 0));
    }

    private void mostrarStatus(String texto, Color cor) {
        status.setForeground(cor);
        status.setText(texto);
    }

    private void registrarTrafego(String linha) {
        trafego.append("[" + LocalTime.now().format(HORA) + "] " + linha + "\n");
    }

    // ============================================================ layout

    private JButton acao(JButton botao) {
        acoes.add(botao);
        return botao;
    }

    private static JPanel formulario(String titulo, String[] rotulos, JComponent[] campos) {
        JPanel painel = new JPanel(new GridBagLayout());
        if (titulo != null) {
            painel.setBorder(BorderFactory.createTitledBorder(titulo));
        } else {
            painel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        }
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < rotulos.length; i++) {
            c.gridx = 0;
            c.gridy = i;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            painel.add(new JLabel(rotulos[i]), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            painel.add(campos[i], c);
        }
        return painel;
    }

    private static void adicionarLinha(JPanel formulario, JComponent componente) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = GridBagConstraints.RELATIVE;
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        formulario.add(componente, c);
    }

    private static JPanel noTopo(JComponent componente) {
        JPanel painel = new JPanel(new BorderLayout());
        painel.add(componente, BorderLayout.NORTH);
        return painel;
    }

    private static JLabel dica(String... linhas) {
        JLabel rotulo = new JLabel("<html><small>" + String.join("<br>", linhas) + "</small></html>");
        rotulo.setForeground(Color.DARK_GRAY);
        return rotulo;
    }

    /** Usa o visual Nimbus (igual em Linux e Windows). Chamar antes de criar a janela. */
    public static void aplicarVisual() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignorada) {
            // fica o visual padrão
        }
    }
}
