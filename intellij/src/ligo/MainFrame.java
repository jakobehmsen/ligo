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
import java.math.MathContext;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
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
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    consoleRun();
                }
            }
        });

        setLayout(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, canvas, new JScrollPane(console));

        splitPane.setOneTouchExpandable(true);
        splitPane.setResizeWeight(0.75);

        getContentPane().add(splitPane, BorderLayout.CENTER);

        // Define initial functions

        functionMap.define("+", BigDecimal.class, BigDecimal.class, (lhs, rhs) -> lhs.add(rhs));
        functionMap.define("-", BigDecimal.class, BigDecimal.class, (lhs, rhs) -> lhs.subtract(rhs));
        functionMap.define("/", BigDecimal.class, BigDecimal.class, (lhs, rhs) -> lhs.divide(rhs, MathContext.DECIMAL128));
        functionMap.define("*", BigDecimal.class, BigDecimal.class, (lhs, rhs) -> lhs.multiply(rhs));

        functionMap.define("color", String.class, (colorName) -> {
            Color color = null;

            if (colorName.startsWith("#")) {
                int red = -1, green = -1, blue = -1;

                if (colorName.length() == 7) {
                    red = parseHexColor(colorName.substring(1, 3));
                    green = parseHexColor(colorName.substring(3, 5));
                    blue = parseHexColor(colorName.substring(5, 7));
                } else if (colorName.length() == 4) {
                    red = parseHexColor(colorName.substring(1, 2));
                    green = parseHexColor(colorName.substring(2, 3));
                    blue = parseHexColor(colorName.substring(3, 4));
                }

                color = new Color(red, green, blue);
            } else {
                color = Color.getColor(colorName);
                if (color == null) {
                    try {
                        color = (Color) Color.class.getField(colorName).get(null);
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            return color;
        });

        functionMap.define("font", String.class, String.class, BigDecimal.class, (fontFamily, styleStr, size) -> {
            int style = Arrays.asList(styleStr.split("\\s+")).stream()
                .map(x -> x.trim()).filter(x -> x.length() > 0)
                .mapToInt(x -> parseStyle(x)).reduce(Font.PLAIN, (x, y) -> x | y);

            return new Font(fontFamily, style, size.intValue());
        });

        // Define initial procedures

        rendererMap.define("setColor", Color.class, (g, color) -> g.setColor(color));
        rendererMap.define("fillRect", BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class, (g, x, y, w, h) ->
            g.fillRect(x.intValue(), y.intValue(), w.intValue(), h.intValue()));
        rendererMap.define("fillOval", BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class, (g, x, y, w, h) ->
            g.fillOval(x.intValue(), y.intValue(), w.intValue(), h.intValue()));
        rendererMap.define("drawString", String.class, BigDecimal.class, BigDecimal.class, (g, str, x, y) ->
            g.drawString(str, x.intValue(), y.intValue()));
        rendererMap.define("setFont", Font.class, (g, font) ->
            g.setFont(font));
    }

    private int parseHexColor(String hex) {
        switch(hex.length()) {
            case 1:
                return Integer.parseInt(hex, 16) * 16;
            case 2:
                return Integer.parseInt(hex, 16);
        }

        return -1;
    }

    private int parseStyle(String style) {
        switch (style.toLowerCase()) {
            case "b":case "bold":
                return Font.BOLD;
            case "i":case "italic":
                return Font.ITALIC;
            case "p":case "plain":
                return Font.PLAIN;
        }

        return -1;
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
                Function<Object[], Cell> valueExpression = parseExpression(ctx.value, self);
                
                return args -> {
                    DictCell target = self;

                    for(int i = 0; i < ctx.ID().size() - 1; i++) {
                        String accessId = ctx.ID().get(i).getText();
                        target = (DictCell)target.getValueCell(accessId);
                    }

                    String id = ctx.ID().get(ctx.ID().size() - 1).getText();

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

                    private Allocation<Consumer<Graphics>> graphicsAllocation;

                    private void update() {
                        if(Arrays.asList(arguments).stream().allMatch(x -> x != null)) {
                            if(graphicsAllocation == null)
                                graphicsAllocation = createGraphicsConsumer();

                            Class<?>[] parameterTypes = Arrays.asList(arguments).stream().map(x -> x.getClass()).toArray(s -> new Class<?>[s]);
                            BiConsumer<Graphics, Object[]> renderer = rendererMap.resolve(name, parameterTypes);

                            graphicsAllocation.set(graphics -> {
                                renderer.accept(graphics, arguments);
                            });
                        }
                    }
                };
            }
        });
    }

    private Allocation<Consumer<Graphics>> createGraphicsConsumer() {
        int index = graphicsConsumers.size();

        Allocation<Consumer<Graphics>> allocation = new Allocation<Consumer<Graphics>>() {
            @Override
            public void set(Consumer<Graphics> value) {
                graphicsConsumers.set(index, value);
                canvas.repaint();
            }
        };
        graphicsConsumers.add(null);

        return allocation;
    }

    private ArrayList<Consumer<Graphics>> graphicsConsumers = new ArrayList<>();
    private FunctionMap functionMap = new FunctionMap();
    private RendererMap rendererMap = new RendererMap();

    private Function<Object[], Cell> parseExpression(ParserRuleContext ctx, DictCell self) {
        return ctx.accept(new LigoBaseVisitor<Function<Object[], Cell>>() {
            @Override
            public Function<Object[], Cell> visitLeafExpression(@NotNull LigoParser.LeafExpressionContext ctx) {
                Function<Object[], Cell> targetExpression = ctx.getChild(0).accept(this);
                Function<Object[], Cell> expression = targetExpression;

                // Be sensitive to the address, not the current cell at the address
                for(LigoParser.IdContext idCtx: ctx.accessChain().id()) {
                    String id = idCtx.getText();
                    Function<Object[], Cell> targetExpressionTmp = targetExpression;
                    expression = args -> {
                        Cell target = targetExpressionTmp.apply(args);
                        return new Cell() {
                            @Override
                            public Binding consume(CellConsumer consumer) {
                                return target.consume(x -> {
                                    Object value = ((Map<String, Object>)x).get(id);

                                    consumer.next(value);
                                });
                            }
                        };
                    };
                    targetExpression = expression;
                }

                return expression;
            }

            @Override
            public Function<Object[], Cell> visitNumber(@NotNull LigoParser.NumberContext ctx) {
                BigDecimal value = new BigDecimal(ctx.NUMBER().getText());
                return args -> new Singleton<>(value);
            }

            @Override
            public Function<Object[], Cell> visitString(@NotNull LigoParser.StringContext ctx) {
                String rawValue = ctx.STRING().getText().substring(1, ctx.STRING().getText().length() - 1);
                String value = rawValue.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");
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

            @Override
            public Function<Object[], Cell> visitAddExpression(@NotNull LigoParser.AddExpressionContext ctx) {
                Function<Object[], Cell> lhs = parseExpression(ctx.mulExpression(0), self);

                if (ctx.mulExpression().size() > 1) {
                    for (int i = 1; i < ctx.mulExpression().size(); i++) {
                        Function<Object[], Cell> rhsCell = parseExpression(ctx.mulExpression(i), self);

                        Function<Object[], Cell> lhsCell = lhs;

                        String operator = ctx.ADD_OP(i - 1).getText();

                        lhs = createFunctionCall(operator, Arrays.asList(lhsCell, rhsCell));
                    }
                }

                return lhs;
            }

            @Override
            public Function<Object[], Cell> visitMulExpression(@NotNull LigoParser.MulExpressionContext ctx) {
                Function<Object[], Cell> lhs = parseExpression(ctx.leafExpression(0), self);

                if (ctx.leafExpression().size() > 1) {
                    for (int i = 1; i < ctx.leafExpression().size(); i++) {
                        Function<Object[], Cell> rhsCell = parseExpression(ctx.leafExpression(i), self);

                        Function<Object[], Cell> lhsCell = lhs;

                        String operator = ctx.MUL_OP(i - 1).getText();

                        lhs = createFunctionCall(operator, Arrays.asList(lhsCell, rhsCell));
                    }
                }

                return lhs;
            }

            @Override
            public Function<Object[], Cell> visitCall(@NotNull LigoParser.CallContext ctx) {
                String name = ctx.ID().getText();
                List<Function<Object[], Cell>> argumentExpressions = ctx.expression().stream().map(x -> parseExpression(x, self)).collect(Collectors.toList());

                return createFunctionCall(name, argumentExpressions);
            }
        });
    }

    private Function<Object[], Cell> createFunctionCall(String name, List<Function<Object[], Cell>> argumentExpressions) {
        Object[] arguments = new Object[argumentExpressions.size()];

        return args -> new Cell() {
            @Override
            public Binding consume(CellConsumer consumer) {
                return new Binding() {
                    FunctionMap.GenericFunction genericFunction = functionMap.getGenericFunction(new FunctionMap.GenericSelector(name, argumentExpressions.size()));
                    Binding genericFunctionBinding = genericFunction.consume(f -> {
                        this.functions = f;
                        update();
                    });
                    Map<FunctionMap.SpecificSelector, FunctionMap.SpecificFunctionInfo> functions;
                    List<Cell> argumentCells = argumentExpressions.stream().map(x -> x.apply(args)).collect(Collectors.toList());
                    List<Binding> argumentBindings = IntStream.range(0, argumentExpressions.size()).mapToObj(i -> {
                        return argumentCells.get(i).consume(x -> {
                            arguments[i] = x;
                            update();
                        });
                    }).collect(Collectors.toList());

                    @Override
                    public void remove() {
                        argumentBindings.forEach(x -> x.remove());
                        genericFunctionBinding.remove();
                    }

                    private void update() {
                        if(Arrays.asList(arguments).stream().allMatch(x -> x != null)) {
                            Object[] callArgs = arguments;
                            Class<?>[] parameterTypes = Arrays.asList(callArgs).stream().map(x -> x.getClass()).toArray(s -> new Class<?>[callArgs.length]);

                            if(functions != null) {
                                FunctionMap.SpecificFunctionInfo function = FunctionMap.GenericFunction.resolve(functions, parameterTypes);

                                if(function != null) {
                                    Object[] locals = new Object[function.localCount];
                                    System.arraycopy(callArgs, 0, locals, 0, callArgs.length);

                                    Object next = function.body.apply(locals);
                                    consumer.next(next);
                                }
                            }
                        }
                    }
                };
            }
        };
    }
}
