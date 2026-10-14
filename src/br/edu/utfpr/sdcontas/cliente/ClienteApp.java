package br.edu.utfpr.sdcontas.cliente;

import br.edu.utfpr.sdcontas.cliente.ui.JanelaCliente;
import javax.swing.SwingUtilities;

/** Ponto de entrada do cliente: {@code java -jar cliente.jar}. */
public final class ClienteApp {

    private ClienteApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JanelaCliente.aplicarVisual();
            new JanelaCliente().setVisible(true);
        });
    }
}
