package ligo;

import ligo.lang.antlr4.LigoBaseVisitor;
import ligo.lang.antlr4.LigoLexer;
import ligo.lang.antlr4.LigoParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.NotNull;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.*;
import java.util.stream.IntStream;

public class MainFrame extends JFrame {
    public static final String PRODUCT_NAME = "Ligo";

    private JPanel canvas;
    private JPanel console;
    private JTextPane consoleHistory;
    private JTextPane consolePending;

    public MainFrame() {
        setTitle(PRODUCT_NAME);

        canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                graphicsConsumers.forEach(x -> x.accept(g));
            }
        };

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

            run(programCtx);

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

    private void run(LigoParser.ProgramContext programCtx) {
        programCtx.statement().forEach(x -> {
            Consumer<Object[]> statement = parseStatement(x, globals);
            statement.accept(new Object[]{});
        });
    }

    private DictCell globals = new DictCell();

    private Consumer<Object[]> parseStatement(ParserRuleContext ctx, DictCell self) {
        return ctx.accept(new LigoBaseVisitor<Consumer<Object[]>>() {
            @Override
            public Consumer<Object[]> visitAssign(@NotNull LigoParser.AssignContext ctx) {
                String id = ctx.ID().getText();
                Function<Object[], Cell> valueExpression = parseExpression(ctx.value, self);
                
                return args -> {
                    DictCell target;

                    if(ctx.target != null) {
                        //Function<Object[], Cell> targetExpression = parseExpression(ctx.target, self);
                        //target = (DictCell)targetExpression.apply(args);
                        Function<Object[], Cell> targetExpression = parseTargetExpression(ctx.target, self);
                        target = (DictCell)targetExpression.apply(args);
                    } else {
                        target = self;
                    }

                    Cell valueCell = valueExpression.apply(args);
                    target.put(id, valueCell);
                };
            }

            @Override
            public Consumer<Object[]> visitCall(@NotNull LigoParser.CallContext ctx) {
                String name = ctx.ID().getText();
                List<Function<Object[], Cell>> argumentExpressions = ctx.expression().stream().map(x -> parseExpression(x, self)).collect(Collectors.toList());

                Object[] arguments = new Object[argumentExpressions.size()];

                return new Consumer<Object[]>() {
                    @Override
                    public void accept(Object[] args) {
                        List<Cell> argumentCells = argumentExpressions.stream().map(x -> x.apply(args)).collect(Collectors.toList());
                        List<Binding> argumentBindings = IntStream.range(0, argumentExpressions.size()).mapToObj(i -> {
                            return argumentCells.get(i).consume(x -> {
                                arguments[i] = x;
                                update();
                            });
                        }).collect(Collectors.toList());
                    }

                    private Binding graphicsBinding;

                    private void update() {
                        if(Arrays.asList(arguments).stream().allMatch(x -> x != null)) {
                            switch(name) {
                                case "fillRect": {
                                    if(graphicsBinding != null)
                                        graphicsBinding.remove();

                                    graphicsBinding = addGraphicsConsumer(g -> {
                                        BigDecimal x = (BigDecimal) arguments[0];
                                        BigDecimal y = (BigDecimal) arguments[1];
                                        BigDecimal width = (BigDecimal) arguments[2];
                                        BigDecimal height = (BigDecimal) arguments[3];
                                        g.fillRect(x.intValue(), y.intValue(), width.intValue(), height.intValue());
                                    });
                                }
                            }
                        }
                    }
                };
            }
        });
    }

    private Function<Object[], Cell> parseTargetExpression(LigoParser.ExpressionContext target, DictCell self) {
        return target.accept(new LigoBaseVisitor<Function<Object[], Cell>>() {
            @Override
            public Function<Object[], Cell> visitId(@NotNull LigoParser.IdContext ctx) {
                String id = ctx.ID().getText();

                return args -> self.getValueCell(id);
            }
        });
    }

    private Binding addGraphicsConsumer(Consumer<Graphics> graphicsConsumer) {
        graphicsConsumers.add(graphicsConsumer);
        canvas.repaint();

        return () -> graphicsConsumers.remove(graphicsConsumer);
    }

    private ArrayList<Consumer<Graphics>> graphicsConsumers = new ArrayList<>();

    private Function<Object[], Cell> parseExpression(ParserRuleContext ctx, DictCell self) {
        return ctx.accept(new LigoBaseVisitor<Function<Object[], Cell>>() {
            @Override
            public Function<Object[], Cell> visitNumber(@NotNull LigoParser.NumberContext ctx) {
                BigDecimal value = new BigDecimal(ctx.NUMBER().getText());
                return args -> new Singleton<>(value);
            }

            @Override
            public Function<Object[], Cell> visitObject(@NotNull LigoParser.ObjectContext ctx) {
                return args -> {
                    DictCell obj = new DictCell();

                    ctx.statement().forEach(x -> {
                        Consumer<Object[]> statement = parseStatement(x, obj);
                        statement.accept(new Object[]{});
                    });

                    return obj;
                };
            }

            @Override
            public Function<Object[], Cell> visitId(@NotNull LigoParser.IdContext ctx) {
                String id = ctx.ID().getText();

                return args -> self.get(id);
            }
        });
    }
}
