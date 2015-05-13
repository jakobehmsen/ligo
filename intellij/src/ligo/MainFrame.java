package ligo;

import ligo.lang.antlr4.LigoLexer;
import ligo.lang.antlr4.LigoParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.BitSet;

public class MainFrame extends JFrame {
    public static final String PRODUCT_NAME = "Ligo";

    private JPanel canvas;
    private JPanel console;
    private JTextPane consoleHistory;
    private JTextPane consolePending;

    public MainFrame() {
        setTitle(PRODUCT_NAME);

        canvas = new JPanel();

        console = new JPanel(new BorderLayout());
        consoleHistory = new JTextPane();
        consoleHistory.setFont(new Font(Font.MONOSPACED, Font.BOLD | Font.ITALIC, 12));
        consoleHistory.setEditable(false);
        consolePending = new JTextPane();
        consolePending.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        console.add(consolePending, BorderLayout.NORTH);
        console.add(consoleHistory, BorderLayout.CENTER);

        DefaultCaret consoleHistoryCaret = (DefaultCaret)consoleHistory.getCaret();
        consoleHistoryCaret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        consolePending.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    consoleRun();
                }
            }
        });

        setLayout(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, canvas, new JScrollPane(console));

        splitPane.setOneTouchExpandable(true);
        splitPane.setResizeWeight(0.75);

        getContentPane().add(splitPane, BorderLayout.CENTER);
    }

    private final SimpleAttributeSet okAttributeSet;
    private final SimpleAttributeSet notOKAttributeSet;
    private final SimpleAttributeSet separatorAttributeSet;

    {
        okAttributeSet = new SimpleAttributeSet();
        StyleConstants.setForeground(okAttributeSet, new Color(10, 50, 10));

        notOKAttributeSet = new SimpleAttributeSet();
        StyleConstants.setForeground(notOKAttributeSet, Color.RED);

        separatorAttributeSet = new SimpleAttributeSet();
        StyleConstants.setFontSize(separatorAttributeSet, 3);
    }

    private void consoleRun() {
        String code = consolePending.getText();
        consolePending.setText("");

        try {
            final StringBuilder errors = new StringBuilder();

            ANTLRInputStream in = new ANTLRInputStream(new ByteArrayInputStream(code.getBytes()));

            LigoLexer lexer = new LigoLexer(in);

            lexer.removeErrorListeners();
            lexer.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                    errors.append("(" + line + "," + charPositionInLine + "): " + msg + "\n");
                }
            });

            LigoParser parser = new LigoParser(new CommonTokenStream(lexer));

            parser.removeErrorListeners();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                    errors.append("(" + line + "," + charPositionInLine + "): " + msg + "\n");
                }
            });

            LigoParser.ProgramContext programCtx = parser.program();

            String result;
            AttributeSet attr;

            if (errors.length() == 0) {
                result = code + "\n";
                attr = okAttributeSet;
            } else {
                result = code + "\n" + errors;
                attr = notOKAttributeSet;
            }

            try {
                consoleHistory.getDocument().insertString(0, result, attr);
                consoleHistory.getDocument().insertString(result.length(), "\n", separatorAttributeSet);
                consoleHistory.getHighlighter().addHighlight(result.length(), result.length() + 1, new Highlighter.HighlightPainter() {
                    @Override
                    public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
                        Rectangle r = null;
                        try {
                            r = c.modelToView(offs0);
                        } catch (BadLocationException e) {
                            e.printStackTrace();
                        }
                        g.setColor(new Color(220, 220, 220));
                        g.fillRect(0, r.y, c.getWidth(), r.height);
                    }
                });
            } catch (BadLocationException e1) {
                e1.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
