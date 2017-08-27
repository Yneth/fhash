package ua.fhash.ftree;

import java.util.function.BiFunction;

public interface FileTreeReducer<T> extends BiFunction<T, T, T> {
}
