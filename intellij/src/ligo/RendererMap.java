package ligo;

import java.awt.*;
import java.util.Hashtable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RendererMap {
    private Hashtable<Selector, BiConsumer<Graphics, Object[]>> renderers = new Hashtable<>();

    public void define(String name, Class<?>[] parameterTypes, BiConsumer<Graphics, Object[]> renderer) {
        renderers.put(new Selector(name, parameterTypes), renderer);
    }

    public <P0> void define(String name, Class<P0> param1, BiConsumer<Graphics, P0> renderer) {
        define(name, new Class<?>[]{param1}, (graphics, args) -> renderer.accept(graphics, (P0) args[0]));
    }

    public <P0, P1, P2> void define(String name, Class<P0> param1, Class<P1> param2, Class<P2> param3, QuadConsumer<Graphics, P0, P1, P2> renderer) {
        define(name, new Class<?>[]{param1, param2, param3}, (graphics, args) -> renderer.accept(graphics, (P0) args[0], (P1) args[1], (P2) args[2]));
    }

    public <P0, P1, P2, P3> void define(String name, Class<P0> param1, Class<P1> param2, Class<P2> param3, Class<P3> param4, QuinConsumer<Graphics, P0, P1, P2, P3> renderer) {
        define(name, new Class<?>[]{param1, param2, param3, param4}, (graphics, args) -> renderer.accept(graphics, (P0) args[0], (P1) args[1], (P2) args[2], (P3) args[3]));
    }

    public BiConsumer<Graphics, Object[]> resolve(String name, Class<?>[] parameterTypes) {
        java.util.List<BiConsumer<Graphics, Object[]>> candidates = renderers.entrySet().stream()
            .filter(x ->
                x.getKey().name.equals(name) && x.getKey().parameterTypes.length == parameterTypes.length)
            .filter(x ->
                IntStream.range(0, parameterTypes.length).allMatch(i ->
                    x.getKey().parameterTypes[i].isAssignableFrom(parameterTypes[i])))
            .map(x -> x.getValue())
            .collect(Collectors.toList());

        if(candidates.size() > 0) {
            // TODO: Compare candidates; select "most specific".
            return candidates.get(0);
        }

        return null;
    }
}
