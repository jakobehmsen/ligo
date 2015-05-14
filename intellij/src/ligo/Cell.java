package ligo;

public interface Cell<T> {
    Binding consume(CellConsumer<T> consumer);
}
