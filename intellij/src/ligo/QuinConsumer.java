package ligo;

public interface QuinConsumer<T, U, V, X, Y> {
    void accept(T t, U u, V v, X x, Y y);
}
