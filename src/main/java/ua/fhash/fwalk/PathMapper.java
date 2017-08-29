package ua.fhash.fwalk;

import java.nio.file.Path;
import java.util.function.Function;

public interface PathMapper<T> extends Function<Path, T> {
}
