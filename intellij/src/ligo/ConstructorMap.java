package ligo;

import java.util.Hashtable;
import java.util.function.Supplier;

public class ConstructorMap {
    private Hashtable<String, Supplier<Cell>> constructors = new Hashtable<>();

    public void define(String name, Supplier<Cell> constructor) {
        constructors.put(name, constructor);
    }

    public Supplier<Cell> resolve(String name) {
        return constructors.get(name);
    }
}
