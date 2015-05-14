package ligo;

public interface CellConsumer<T> {
    void next(T value);
}
