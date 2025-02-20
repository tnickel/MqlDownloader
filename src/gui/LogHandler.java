package gui;



import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LogHandler {
    private final JTextArea logArea;
    private static final Logger logger = LogManager.getLogger(LogHandler.class);
    private final JScrollPane scrollPane;

    public LogHandler() {
        this.logArea = createLogArea();
        this.scrollPane = createScrollPane();
    }

    private JTextArea createLogArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setBackground(Color.BLACK);
        area.setForeground(Color.GREEN);
        area.setMargin(new Insets(5,5,5,5));
        return area;
    }

    private JScrollPane createScrollPane() {
        JScrollPane pane = new JScrollPane(logArea);
        pane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Log Output", 
            TitledBorder.LEFT, 
            TitledBorder.TOP));
        return pane;
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + java.time.LocalTime.now().toString() + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        logger.info(message);
    }

    public void logError(String message, Exception e) {
        log(message);
        logger.error(message, e);
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> logArea.setText(""));
    }
}