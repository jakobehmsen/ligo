package ligo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FunctionMap {
    public static class SpecificSelector {
        private final Class<?>[] parameterTypes;

        private SpecificSelector(Class<?>[] parameterTypes) {
            this.parameterTypes = parameterTypes;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(parameterTypes);
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof SpecificSelector) {
                SpecificSelector objSelector = (SpecificSelector)obj;
                return Arrays.equals(this.parameterTypes, objSelector.parameterTypes);
            }

            return false;
        }
    }

    public static class SpecificFunctionInfo {
        public final int localCount;
        public final Function<Object[], Object> body;

        public SpecificFunctionInfo(int localCount, Function<Object[], Object> body) {
            this.localCount = localCount;
            this.body = body;
        }
    }

    public static class GenericFunction implements Cell<Map<SpecificSelector, SpecificFunctionInfo>> {
        private Hashtable<SpecificSelector, SpecificFunctionInfo> applicableSpecificFunctions = new Hashtable<>();
        private ArrayList<CellConsumer<Map<SpecificSelector, SpecificFunctionInfo>>> consumers = new ArrayList<>();

        public void define(Class<?>[] parameterTypes, SpecificFunctionInfo genericFunction) {
            SpecificSelector specificSelector = new SpecificSelector(parameterTypes);
            applicableSpecificFunctions.put(specificSelector, genericFunction);
            update();
        }

        private void update() {
            consumers.forEach(x -> x.next((Map<SpecificSelector, SpecificFunctionInfo>) applicableSpecificFunctions.clone()));
        }

        @Override
        public Binding consume(CellConsumer<Map<SpecificSelector, SpecificFunctionInfo>> consumer) {
            consumers.add(consumer);

            consumer.next((Map<SpecificSelector, SpecificFunctionInfo>) applicableSpecificFunctions.clone());

            return () -> consumers.remove(consumer);
        }

        public static SpecificFunctionInfo resolve(Map<SpecificSelector, SpecificFunctionInfo> functions, Class<?>[] parameterTypes) {
            java.util.List<SpecificFunctionInfo> candidates = functions.entrySet().stream()
                .filter(x ->
                    x.getKey().parameterTypes.length == parameterTypes.length)
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

    public static class Selector {
        private final String name;
        private final Class<?>[] parameterTypes;

        private Selector(String name, Class<?>[] parameterTypes) {
            this.name = name;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public int hashCode() {
            return name.hashCode() * Arrays.hashCode(parameterTypes);
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof Selector) {
                Selector objSelector = (Selector)obj;
                return this.name.equals(objSelector.name) &&
                    Arrays.equals(this.parameterTypes, objSelector.parameterTypes);
            }

            return false;
        }
    }

    public static class GenericSelector {
        private final String name;
        private final int arity;

        public GenericSelector(String name, int arity) {
            this.name = name;
            this.arity = arity;
        }

        @Override
        public int hashCode() {
            return name.hashCode() * arity;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof GenericSelector) {
                GenericSelector objSelector = (GenericSelector)obj;
                return this.name.equals(objSelector.name) &&
                    this.arity == objSelector.arity;
            }

            return false;
        }
    }

    public void define(String name, Class<?>[] parameterTypes, int localCount, Function<Object[], Object> function) {
        GenericFunction genericFunction = getGenericFunction(new GenericSelector(name, parameterTypes.length));
        genericFunction.define(parameterTypes, new SpecificFunctionInfo(localCount, function));
    }

    public void define(String name, Class<?>[] parameterTypes, Function<Object[], Object> function) {
        define(name, parameterTypes, parameterTypes.length, function);
    }

    public <Return> void define(String name, Supplier<Return> function) {
        define(name, new Class<?>[0], args -> function.get());
    }

    public <P0, Return> void define(String name, Class<P0> param1, Function<P0, Return> function) {
        define(name, new Class<?>[]{param1}, args -> function.apply((P0) args[0]));
    }

    public <P0, P1, Return> void define(String name, Class<P0> param1, Class<P1> param2, BiFunction<P0, P1, Return> function) {
        define(name, new Class<?>[]{param1, param2}, args -> function.apply((P0)args[0], (P1)args[1]));
    }

    private Hashtable<Selector, Binding> functionBindings = new Hashtable<>();
    private Hashtable<GenericSelector, GenericFunction> genericFunctions = new Hashtable<>();

    public GenericFunction getGenericFunction(GenericSelector selector) {
        GenericFunction functionBinding = genericFunctions.get(selector);
        if(functionBinding == null) {
            functionBinding = new GenericFunction();
            genericFunctions.put(selector, functionBinding);
        }
        return functionBinding;
    }
}
