package br.edu.utfpr.sdcontas.servidor.ui;

import br.edu.utfpr.sdcontas.servidor.RegistroLog;
import br.edu.utfpr.sdcontas.servidor.ServidorContas;
import br.edu.utfpr.sdcontas.servidor.Sessoes;
import br.edu.utfpr.sdcontas.servidor.Usuario;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;

/** Janela do servidor: escolher porta, ligar/desligar, ver log, usuários e sessões ativas. */
public final class JanelaServidor extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final int LIMITE_LOG = 400_000; // caracteres mantidos na tela
    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault());

    private final transient Path arquivoUsuarios;
    private final transient RegistroLog log = new RegistroLog();
    private transient ServidorContas servidor;

    private final JSpinner campoPorta;
    private final JButton botaoIniciar = new JButton("Iniciar");
    private final JButton botaoParar = new JButton("Parar");
    private final JLabel status = new JLabel();
    private final JLabel enderecos = new JLabel();
    private final JTextArea areaLog = new JTextArea();
    private final DefaultTableModel modeloUsuarios = tabelaSomenteLeitura("Nome (name)", "Username");
    private final DefaultTableModel modeloSessoes = tabelaSomenteLeitura("Username", "Token", "Desde");

    public JanelaServidor(int portaInicial, Path arquivoUsuarios) {
        super("SD-Contas · Servidor");
        this.arquivoUsuarios = arquivoUsuarios;

        campoPorta = new JSpinner(new SpinnerNumberModel(portaInicial, 1, 65535, 1));
        campoPorta.setEditor(new JSpinner.NumberEditor(campoPorta, "#"));

        JPanel topo = new JPanel(new BorderLayout());
        JPanel controles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        controles.add(new JLabel("Porta:"));
        controles.add(campoPorta);
        controles.add(botaoIniciar);
        controles.add(botaoParar);
        controles.add(status);
        topo.add(controles, BorderLayout.NORTH);
        enderecos.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));
        topo.add(enderecos, BorderLayout.SOUTH);

        areaLog.setEditable(false);
        areaLog.setLineWrap(true);
        areaLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ((DefaultCaret) areaLog.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JButton limpar = new JButton("Limpar log");
        limpar.addActionListener(e -> areaLog.setText(""));
        JPanel painelLog = new JPanel(new BorderLayout());
        painelLog.add(new JScrollPane(areaLog), BorderLayout.CENTER);
        JPanel rodapeLog = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rodapeLog.add(limpar);
        painelLog.add(rodapeLog, BorderLayout.SOUTH);

        JTabbedPane abas = new JTabbedPane();
        abas.addTab("Log", painelLog);
        abas.addTab("Usuários", new JScrollPane(new JTable(modeloUsuarios)));
        abas.addTab("Sessões ativas", new JScrollPane(new JTable(modeloSessoes)));

        add(topo, BorderLayout.NORTH);
        add(abas, BorderLayout.CENTER);

        log.adicionarOuvinte(linha -> SwingUtilities.invokeLater(() -> anexarLog(linha)));
        botaoIniciar.addActionListener(e -> iniciar());
        botaoParar.addActionListener(e -> parar());
        new Timer(1000, e -> atualizarTabelas()).start();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                parar();
                dispose();
                System.exit(0);
            }
        });

        atualizarEstado();
        setSize(980, 620);
        setLocationRelativeTo(null);
    }

    private void iniciar() {
        int porta = (Integer) campoPorta.getValue();
        try {
            servidor = new ServidorContas(porta, arquivoUsuarios, log);
            servidor.iniciar();
        } catch (IOException e) {
            servidor = null;
            JOptionPane.showMessageDialog(this, "Não foi possível abrir a porta " + porta + ":\n" + e.getMessage(),
                    "Erro ao iniciar", JOptionPane.ERROR_MESSAGE);
        }
        atualizarEstado();
        atualizarTabelas();
    }

    private void parar() {
        if (servidor != null) {
            servidor.parar();
            servidor = null;
        }
        atualizarEstado();
        atualizarTabelas();
    }

    private void atualizarEstado() {
        boolean rodando = servidor != null && servidor.isRodando();
        botaoIniciar.setEnabled(!rodando);
        botaoParar.setEnabled(rodando);
        campoPorta.setEnabled(!rodando);
        if (rodando) {
            status.setText("● Ouvindo na porta " + servidor.getPorta());
            status.setForeground(new Color(0, 128, 0));
            enderecos.setText("Para conectar de outra máquina use: " + String.join("   ", enderecosLocais(servidor.getPorta())));
        } else {
            status.setText("● Parado");
            status.setForeground(Color.GRAY);
            enderecos.setText("Usuários gravados em " + arquivoUsuarios.toAbsolutePath());
        }
    }

    private void atualizarTabelas() {
        modeloUsuarios.setRowCount(0);
        modeloSessoes.setRowCount(0);
        if (servidor == null) {
            return;
        }
        for (Usuario u : servidor.listarUsuarios()) {
            modeloUsuarios.addRow(new Object[] {u.name(), u.username()});
        }
        for (Sessoes.Sessao s : servidor.listarSessoes()) {
            modeloSessoes.addRow(new Object[] {s.username(), s.token(), HORA.format(s.inicio())});
        }
    }

    private void anexarLog(String linha) {
        areaLog.append(linha + "\n");
        int excesso = areaLog.getDocument().getLength() - LIMITE_LOG;
        if (excesso > 0) {
            try {
                areaLog.getDocument().remove(0, excesso);
            } catch (BadLocationException ignorada) {
                // não acontece: o intervalo está dentro do documento
            }
        }
    }

    /** IPv4 desta máquina (exceto loopback), para os colegas saberem onde conectar. */
    private static List<String> enderecosLocais(int porta) {
        List<String> lista = new ArrayList<>();
        try {
            for (NetworkInterface placa : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!placa.isUp() || placa.isLoopback()) {
                    continue;
                }
                for (InetAddress endereco : Collections.list(placa.getInetAddresses())) {
                    if (endereco instanceof Inet4Address) {
                        lista.add(endereco.getHostAddress() + ":" + porta);
                    }
                }
            }
        } catch (SocketException ignorada) {
            // sem lista de placas: mostra só o localhost
        }
        lista.add("127.0.0.1:" + porta);
        return lista;
    }

    private static DefaultTableModel tabelaSomenteLeitura(String... colunas) {
        return new DefaultTableModel(colunas, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int linha, int coluna) {
                return false;
            }
        };
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
