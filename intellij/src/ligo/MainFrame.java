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
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
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

        functionMap.define("toString", Object.class, v -> v.toString());

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

        functionMap.define("measureString", Font.class, String.class, (font, string) -> {
            // Should be based on a concrete graphics object
            Graphics graphics = getGraphics();
            FontMetrics fm = graphics.getFontMetrics(font);
            Rectangle2D bounds = fm.getStringBounds(string, graphics);
            //Rectangle bounds = font.getStringBounds(string, new FontRenderContext(font.getTransform(), false, false)).getBounds();

            HashMap<String, Object> boundsMap = new HashMap<>();

            boundsMap.put("ascent", new BigDecimal(fm.getAscent()));
            boundsMap.put("descent", new BigDecimal(fm.getDescent()));
            boundsMap.put("width", new BigDecimal(bounds.getWidth()));
            boundsMap.put("height", new BigDecimal(bounds.getHeight()));

            return boundsMap;
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

            // Sometimes, a graphics object is need for some functions (so far measureString)
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
            Function<Object[], Binding> statement = parseStatement(x, globals, 0);
            Binding binding = statement.apply(new Object[]{});
            //globals.addBinding(binding);
            // What to do with the binding?
        });
    }

    private DictCell globals = new DictCell();

    private Function<Object[], Binding> parseStatement(ParserRuleContext ctx, DictCell self, int depth) {
        return ctx.accept(new LigoBaseVisitor<Function<Object[], Binding>>() {
            @Override
            public Function<Object[], Binding> visitAssign(@NotNull LigoParser.AssignContext ctx) {
                //Function<Object[], Cell> valueExpression = parseExpression(ctx.value, self, depth);

                String id = ctx.ID().get(ctx.ID().size() - 1).getText();

                Expression valueExpressionTmp = parseExpression(ctx.value, self, depth);

                DictCell targetTmp = self;

                for(int i = 0; i < ctx.ID().size() - 1; i++) {
                    String accessId = ctx.ID().get(i).getText();
                    targetTmp = (DictCell)targetTmp.getValueCell(accessId);
                }

                String operatorName = null;

                switch(ctx.op.getType()) {
                    case LigoLexer.ASSIGN_OP_ADD:
                        operatorName = "+";
                        break;
                    case LigoLexer.ASSIGN_OP_SUB:
                        operatorName = "-";
                        break;
                    case LigoLexer.ASSIGN_OP_MUL:
                        operatorName = "*";
                        break;
                    case LigoLexer.ASSIGN_OP_DIV:
                        operatorName = "/";
                        break;
                }

                if(operatorName != null) {
                    valueExpressionTmp = createFunctionCall(operatorName, Arrays.asList(
                        createFunctionCall(MACRO_COPY, Arrays.asList(createIdExpression(targetTmp, id))),
                        valueExpressionTmp
                    ));
                }

                Expression valueExpression = valueExpressionTmp;
                DictCell target = targetTmp;
                
                return args -> {
                    Cell valueCell = valueExpression.createValueCell(args);
                    target.put(id, valueCell);

                    return () -> { };
                };
            }

            @Override
            public Function<Object[], Binding> visitCall(@NotNull LigoParser.CallContext ctx) {
                String name = ctx.ID().getText();
                //List<Function<Object[], Cell>> argumentExpressions = ctx.expression().stream().map(x -> parseExpression(x, self, depth)).collect(Collectors.toList());
                List<Expression> argumentExpressions = ctx.expression().stream().map(x -> parseExpression(x, self, depth)).collect(Collectors.toList());

                Object[] arguments = new Object[argumentExpressions.size()];

                return new Function<Object[], Binding>() {
                    @Override
                    public Binding apply(Object[] args) {
                        List<Cell> argumentCells = argumentExpressions.stream().map(x -> x.createValueCell(args)).collect(Collectors.toList());
                        List<Binding> argumentBindings = IntStream.range(0, argumentExpressions.size()).mapToObj(i -> {
                            return argumentCells.get(i).consume(x -> {
                                arguments[i] = x;
                                update();
                            });
                        }).collect(Collectors.toList());

                        Binding binding = () -> {
                            argumentBindings.forEach(x -> x.remove());
                            graphicsAllocation.remove();
                        };

                        self.addBinding(binding);

                        return binding;
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

            @Override
            public Function<Object[], Binding> visitFunctionDefinition(@NotNull LigoParser.FunctionDefinitionContext ctx) {
                String name = ctx.ID().getText();
                int functionDepth = depth + 1;

                ArrayList<VariableInfo> functionLocals = new ArrayList<>();
                if (ctx.parameters() != null)
                    functionLocals.addAll(ctx.parameters().ID().stream().map(x -> new VariableInfo(Object.class, x.getText(), 0)).collect(Collectors.toList()));

                ParserRuleContext bodyTree = ctx.expression();

                Expression bodyCell = parseExpression(bodyTree, self, functionDepth);
                //Function<Object[], Cell> bodyCell = parseExpression(bodyTree, self, functionDepth);

                // Compare in relation to the given function depth
                Stream<VariableInfo> parameters = functionLocals.stream().filter(x -> x.depth == functionDepth);
                Class<?>[] parameterTypes = parameters.map(x -> x.type).toArray(s -> new Class<?>[s]);

                if(parameterTypes.length > 0) {
                    return args -> {
                        Cell<Function<Object[], Object>> cellBody = bodyCell.createFunctionCell(args);
                        functionMap.define(name, parameterTypes, functionLocals.size(), cellBody);

                        return () -> {
                            // How to remove a function definition? Should it be possible?
                        };
                    };
                } else {
                    // Cell constructor definition
                    return args -> {
                        Supplier<Cell> constructor = () -> bodyCell.createValueCell(args);
                        constructorMap.define(name, constructor);

                        return () -> {
                            // How to remove a Cell constructor definition? Should it be possible?
                        };
                    };
                }
            }
        });
    }

    private class RendererAllocation implements Allocation<Consumer<Graphics>>, Consumer<Graphics> {
        Consumer<Graphics> value;

        @Override
        public void set(Consumer<Graphics> value) {
            this.value = value;
            canvas.repaint();
        }

        @Override
        public void remove() {
            graphicsConsumers.remove(this);
        }

        @Override
        public void accept(Graphics graphics) {
            value.accept(graphics);
        }
    }

    private Allocation<Consumer<Graphics>> createGraphicsConsumer() {
        /*int index = graphicsConsumers.size();

        Allocation<Consumer<Graphics>> allocation = new Allocation<Consumer<Graphics>>() {
            @Override
            public void set(Consumer<Graphics> value) {
                graphicsConsumers.set(index, value);
                canvas.repaint();
            }

            @Override
            public void remove() {

            }
        };*/

        RendererAllocation allocation = new RendererAllocation();

        graphicsConsumers.add(allocation);

        return allocation;
    }

    private ArrayList<Consumer<Graphics>> graphicsConsumers = new ArrayList<>();
    private FunctionMap functionMap = new FunctionMap();
    private RendererMap rendererMap = new RendererMap();
    private ConstructorMap constructorMap = new ConstructorMap();

    //private Function<Object[], Cell> parseExpression(ParserRuleContext ctx, DictCell self, int depth) {
    private Expression parseExpression(ParserRuleContext ctx, DictCell self, int depth) {
        return ctx.accept(new LigoBaseVisitor<Expression>() {
            /*@Override
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
            }*/

            @Override
            public Expression visitLeafExpression(@NotNull LigoParser.LeafExpressionContext ctx) {
                Expression targetExpression = ctx.getChild(0).accept(this);
                Expression expression = targetExpression;

                // Be sensitive to the address, not the current cell at the address
                for(LigoParser.IdContext idCtx: ctx.accessChain().id()) {
                    String id = idCtx.getText();
                    Expression targetExpressionTmp = targetExpression;
                    expression = new Expression() {
                        @Override
                        public Cell createValueCell(Object[] args) {
                            Cell target = targetExpressionTmp.createValueCell(args);
                            return new Cell() {
                                @Override
                                public Binding consume(CellConsumer consumer) {
                                    return target.consume(x -> {
                                        Object value = ((Map<String, Object>)x).get(id);

                                        consumer.next(value);
                                    });
                                }
                            };
                        }

                        @Override
                        public Cell<Function<Object[], Object>> createFunctionCell(Object[] args) {
                            Cell<Function<Object[], Object>> target = targetExpressionTmp.createFunctionCell(args);
                            return new Cell<Function<Object[], Object>>() {
                                @Override
                                public Binding consume(CellConsumer<Function<Object[], Object>> consumer) {
                                    return target.consume(x -> {
                                        Function<Object[], Object> value = args -> ((Map<String, Object>)x.apply(args)).get(id);

                                        consumer.next(value);
                                    });
                                }
                            };
                        }
                    };
                    targetExpression = expression;
                }

                return expression;
            }

            /*@Override
            public Function<Object[], Cell> visitNumber(@NotNull LigoParser.NumberContext ctx) {
                BigDecimal value = new BigDecimal(ctx.NUMBER().getText());
                return args -> new Singleton<>(value);
            }*/

            @Override
            public Expression visitNumber(@NotNull LigoParser.NumberContext ctx) {
                BigDecimal value = new BigDecimal(ctx.NUMBER().getText());
                //return args -> new Singleton<>(value);
                return new Expression() {
                    @Override
                    public Cell createValueCell(Object[] args) {
                        return new Singleton<>(value);
                    }

                    @Override
                    public Cell<Function<Object[], Object>> createFunctionCell(Object[] args) {
                        return new Singleton<>(eArgs -> value);
                    }
                };
            }

            /*@Override
            public Function<Object[], Cell> visitString(@NotNull LigoParser.StringContext ctx) {
                String rawValue = ctx.STRING().getText().substring(1, ctx.STRING().getText().length() - 1);
                String value = rawValue.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");
                return args -> new Singleton<>(value);
            }*/

            @Override
            public Expression visitString(@NotNull LigoParser.StringContext ctx) {
                String rawValue = ctx.STRING().getText().substring(1, ctx.STRING().getText().length() - 1);
                String value = rawValue.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");
                //return args -> new Singleton<>(value);
                return new Expression() {
                    @Override
                    public Cell createValueCell(Object[] args) {
                        return new Singleton<>(value);
                    }

                    @Override
                    public Cell<Function<Object[], Object>> createFunctionCell(Object[] args) {
                        return new Singleton<>(eArgs -> value);
                    }
                };
            }

            /*@Override
            public Function<Object[], Cell> visitObject(@NotNull LigoParser.ObjectContext ctx) {
                Function<Object[], Cell<Function<Object[], Object>>> x = args -> {
                    return new Cell<Function<Object[], Object>>() {
                        @Override
                        public Binding consume(CellConsumer<Function<Object[], Object>> consumer) {
                            consumer.next(cArgs -> {
                                DictCell obj = new DictCell();

                                ctx.statement().forEach(x -> {
                                    Consumer<Object[]> statement = parseStatement(x, obj, depth);
                                    statement.accept(new Object[]{});
                                });

                                return obj;
                            });

                            return () -> { };
                        }
                    };
                };

                return args -> {
                    DictCell obj = new DictCell();

                    ctx.statement().forEach(x -> {
                        Consumer<Object[]> statement = parseStatement(x, obj, depth);
                        statement.accept(new Object[]{});
                    });

                    return obj;
                };
            }*/

            @Override
            public Expression visitObject(@NotNull LigoParser.ObjectContext ctx) {
                /*Function<Object[], Cell<Function<Object[], Object>>> x = args -> {
                    return new Cell<Function<Object[], Object>>() {
                        @Override
                        public Binding consume(CellConsumer<Function<Object[], Object>> consumer) {
                            consumer.next(cArgs -> {
                                DictCell obj = new DictCell();

                                ctx.statement().forEach(x -> {
                                    Consumer<Object[]> statement = parseStatement(x, obj, depth);
                                    statement.accept(new Object[]{});
                                });

                                return obj;
                            });

                            return () -> { };
                        }
                    };
                };*/

                return new Expression() {
                    @Override
                    public Cell createValueCell(Object[] args) {
                        DictCell obj = new DictCell();

                        ctx.statement().forEach(x -> {
                            Function<Object[], Binding> statement = parseStatement(x, obj, depth);
                            Binding binding = statement.apply(args);
                            //obj.addBinding(binding);
                            // What to do with the binding?
                        });

                        return obj;
                    }

                    @Override
                    public Cell<Function<Object[], Object>> createFunctionCell(Object[] args) {
                        return new Singleton<>(eArgs -> {
                            DictCell obj = new DictCell();

                            ctx.statement().forEach(x -> {
                                Function<Object[], Binding> statement = parseStatement(x, obj, depth);
                                Binding binding = statement.apply(eArgs);
                                // What to do with the binding?
                            });

                            return obj;
                        });
                    }
                };
            }

            /*@Override
            public Function<Object[], Cell> visitId(@NotNull LigoParser.IdContext ctx) {
                String id = ctx.ID().getText();

                return args -> self.get(id);
            }*/

            @Override
            public Expression visitId(@NotNull LigoParser.IdContext ctx) {
                String id = ctx.ID().getText();

                return createIdExpression(self, id);
            }

            /*@Override
            public Function<Object[], Cell> visitAddExpression(@NotNull LigoParser.AddExpressionContext ctx) {
                Function<Object[], Cell> lhs = parseExpression(ctx.mulExpression(0), self, depth);

                if (ctx.mulExpression().size() > 1) {
                    for (int i = 1; i < ctx.mulExpression().size(); i++) {
                        Function<Object[], Cell> rhsCell = parseExpression(ctx.mulExpression(i), self, depth);

                        Function<Object[], Cell> lhsCell = lhs;

                        String operator = ctx.ADD_OP(i - 1).getText();

                        lhs = createFunctionCall(operator, Arrays.asList(lhsCell, rhsCell));
                    }
                }

                return lhs;
            }*/

            @Override
            public Expression visitAddExpression(@NotNull LigoParser.AddExpressionContext ctx) {
                Expression lhs = parseExpression(ctx.mulExpression(0), self, depth);

                if (ctx.mulExpression().size() > 1) {
                    for (int i = 1; i < ctx.mulExpression().size(); i++) {
                        Expression rhsCell = parseExpression(ctx.mulExpression(i), self, depth);

                        Expression lhsCell = lhs;

                        String operator = ctx.ADD_OP(i - 1).getText();

                        lhs = createFunctionCall(operator, Arrays.asList(lhsCell, rhsCell));
                    }
                }

                return lhs;
            }

            /*@Override
            public Function<Object[], Cell> visitMulExpression(@NotNull LigoParser.MulExpressionContext ctx) {
                Function<Object[], Cell> lhs = parseExpression(ctx.leafExpression(0), self, depth);

                if (ctx.leafExpression().size() > 1) {
                    for (int i = 1; i < ctx.leafExpression().size(); i++) {
                        Function<Object[], Cell> rhsCell = parseExpression(ctx.leafExpression(i), self, depth);

                        Function<Object[], Cell> lhsCell = lhs;

                        String operator = ctx.MUL_OP(i - 1).getText();

                        lhs = createFunctionCall(operator, Arrays.asList(lhsCell, rhsCell));
                    }
                }

                return lhs;
            }*/

            @Override
            public Expression visitMulExpression(@NotNull LigoParser.MulExpressionContext ctx) {
                Expression lhs = parseExpression(ctx.leafExpression(0), self, depth);

                if (ctx.leafExpression().size() > 1) {
                    for (int i = 1; i < ctx.leafExpression().size(); i++) {
                        Expression rhsCell = parseExpression(ctx.leafExpression(i), self, depth);

                        Expression lhsCell = lhs;

                        String operator = ctx.MUL_OP(i - 1).getText();

                        lhs = createFunctionCall(operator, Arrays.asList(lhsCell, rhsCell));
                    }
                }

                return lhs;
            }

            /*@Override
            public Function<Object[], Cell> visitCall(@NotNull LigoParser.CallContext ctx) {
                String name = ctx.ID().getText();
                List<Function<Object[], Cell>> argumentExpressions = ctx.expression().stream().map(x -> parseExpression(x, self, depth)).collect(Collectors.toList());

                return createFunctionCall(name, argumentExpressions);
            }*/

            @Override
            public Expression visitCall(@NotNull LigoParser.CallContext ctx) {
                String name = ctx.ID().getText();
                List<Expression> argumentExpressions = ctx.expression().stream().map(x -> parseExpression(x, self, depth)).collect(Collectors.toList());

                if(argumentExpressions.size() > 0)
                    return createFunctionCall(name, argumentExpressions);
                return new Expression() {
                    @Override
                    public Cell createValueCell(Object[] args) {
                        Supplier<Cell> constructor = constructorMap.resolve(name);
                        return constructor.get();
                    }

                    @Override
                    public Cell<Function<Object[], Object>> createFunctionCell(Object[] args) {
                        // This shouldn't be supported, right?
                        return null;
                    }
                };
            }
        });
    }

    private Expression createIdExpression(DictCell self, String id) {
        return new Expression() {
            @Override
            public Cell createValueCell(Object[] args) {
                return self.get(id);
            }

            @Override
            public Cell<Function<Object[], Object>> createFunctionCell(Object[] args) {
                // Local variable?
                return null;
            }
        };
    }

    /*private Function<Object[], Cell> createFunctionCall(String name, List<Function<Object[], Cell>> argumentExpressions) {
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
    }*/

    private static final String MACRO_COPY = "copy";

    private Expression createFunctionCall(String name, List<Expression> argumentExpressions) {
        // Is macro?

        if(name.equals(MACRO_COPY)) {
            return new Expression() {
                @Override
                public Cell createValueCell(Object[] args) {
                    Cell arg1 = argumentExpressions.get(0).createValueCell(args);

                    return new Cell() {
                        private Binding binding;

                        @Override
                        public Binding consume(CellConsumer consumer) {
                            binding = arg1.consume(value -> {
                                consumer.next(value);
                            });

                            binding.remove();

                            /*

                            Should be something like:

                            Channel channel = arg1.createChannel();
                            channel.consume(value -> {
                                consumer.next(value);
                                channel.remove();
                            });

                            or perhaps:

                            Binding binding = arg1.consume(
                                consumer.next(value);
                                binding.remove();
                            });
                            binding.startConsumption();

                            */

                            return () -> { };
                        }
                    };
                }

                @Override
                public Cell<Function<Object[], Object>> createFunctionCell(Object[] args) {
                    return null;
                }
            };
        }

        Object[] arguments = new Object[argumentExpressions.size()];

        return new Expression() {
            @Override
            public Cell createValueCell(Object[] args) {
                return new Cell() {
                    @Override
                    public Binding consume(CellConsumer consumer) {
                        return new Binding() {
                            FunctionMap.GenericFunction genericFunction = functionMap.getGenericFunction(new FunctionMap.GenericSelector(name, argumentExpressions.size()));
                            Binding genericFunctionBinding = genericFunction.consume(f -> {
                                this.functions = f;
                                update();
                            });
                            Map<FunctionMap.SpecificSelector, FunctionMap.SpecificFunctionInfo> functions;
                            List<Cell> argumentCells = argumentExpressions.stream().map(x -> x.createValueCell(args)).collect(Collectors.toList());
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

            @Override
            public Cell<Function<Object[], Object>> createFunctionCell(Object[] args) {
                return null;
            }
        };
    }
}
