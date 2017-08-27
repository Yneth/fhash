package ua.fhash.ftree;

import java.util.function.Function;

public interface FileTreeMapper<T> extends Function<FileTree.Node, T> {
}
