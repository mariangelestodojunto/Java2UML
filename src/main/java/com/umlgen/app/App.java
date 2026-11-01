package com.umlgen.app;

import com.umlgen.cli.CommandLine;
import com.umlgen.ui.MainWindow;
import com.umlgen.web.WebServer;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;

/**
 * Punto de entrada.
 * <ul>
 *   <li>sin argumentos: interfaz gráfica de escritorio (si no hay pantalla, inicia la interfaz web)</li>
 *   <li>{@code --web [puerto]}: interfaz web (puerto por defecto: variable PORT o 8080)</li>
 *   <li>{@code --cli ...}: línea de comandos</li>
 * </ul>
 */
public final class App {
    public static final String NAME = "Java2UML";
    public static final String VERSION = "2.4.0";

    private App() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--cli".equals(args[0])) {
            System.exit(CommandLine.run(args));
            return;
        }
        if ((args.length > 0 && "--web".equals(args[0])) || GraphicsEnvironment.isHeadless()) {
            try {
                WebServer.main(args.length > 1 ? args[1] : null);
            } catch (Exception e) {
                System.err.println("No se pudo iniciar la interfaz web: " + e.getMessage());
                System.exit(1);
            }
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> MainWindow.showUnexpectedError(e));
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // se usa el aspecto por defecto de Swing
            }
            new MainWindow().setVisible(true);
        });
    }
}
