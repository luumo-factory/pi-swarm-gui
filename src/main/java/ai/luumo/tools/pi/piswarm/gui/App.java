package ai.luumo.tools.pi.piswarm.gui;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;

/**
 * Application entry point for the Pi Swarm Swing GUI.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::createAndShowGui);
    }

    private static void createAndShowGui() {
        JFrame frame = new JFrame("Pi Swarm GUI");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        JLabel label = new JLabel("Pi Swarm GUI", JLabel.CENTER);
        frame.add(label, BorderLayout.CENTER);

        frame.setVisible(true);
    }
}
