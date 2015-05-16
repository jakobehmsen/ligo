package ligo;

import java.util.Arrays;

public class Selector {
    public final String name;
    public final Class<?>[] parameterTypes;

    public Selector(String name, Class<?>[] parameterTypes) {
        this.name = name;
        this.parameterTypes = parameterTypes;
    }

    @Override
    public int hashCode() {
        return name.hashCode() * Arrays.hashCode(parameterTypes);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Selector) {
            Selector objSelector = (Selector) obj;
            return this.name.equals(objSelector.name) &&
                Arrays.equals(this.parameterTypes, objSelector.parameterTypes);
        }

        return false;
    }
}
