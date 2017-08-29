package ua.fhash.fwalk;

import java.util.function.BiFunction;

public interface Reducer<T> extends BiFunction<T, T, T> {
}
