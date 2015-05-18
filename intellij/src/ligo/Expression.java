package ligo;

import java.util.function.Function;

public interface Expression {
    Cell createValueCell(Object[] args);
    Cell<Function<Object[], Object>> createFunctionCell(Object[] args);
}
