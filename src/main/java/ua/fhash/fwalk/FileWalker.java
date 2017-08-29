package ua.fhash.fwalk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static io.vavr.API.unchecked;

public class FileWalker<T> {

    private final Path path;

    public FileWalker(Path path) {
        this.path = Objects.requireNonNull(path);
    }

    public T walk(T seed, PathMapper<T> mapper, Reducer<T> reducer)
            throws IOException {
        return walk(seed, mapper, reducer, reducer);
    }

    public T walk(T seed, PathMapper<T> mapper, Reducer<T> reducer, Reducer<T> dirReducer)
            throws IOException {
        return walk(seed, mapper, reducer, dirReducer);
    }

    private T walk(Path path, T seed, PathMapper<T> mapper,
                   Reducer<T> reducer, Reducer<T> dirReducer)
            throws IOException {
        final File file = path.toFile();
        if (file.isDirectory()) {
            final T foldedChild = Files.list(path)
                    .sorted(Path::compareTo)
                    .map(unchecked(p -> walk(seed, mapper, reducer, dirReducer)))
                    .reduce(seed, reducer::apply);
            return dirReducer.apply(mapper.apply(path), foldedChild);
        }
        return mapper.apply(path);
    }

}
