package ua.fhash.ftree;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.Getter;

import static io.vavr.API.unchecked;

public class FileTree {

    private final Node root;

    private FileTree(Node root) {
        this.root = root;
    }

    public int count() {
        return root.foldMap(0, file -> 1, Integer::sum);
    }

    public int countFiles() {
        return root.foldMap(0, file -> file.isFile() ? 1 : 0, Integer::sum);
    }

    public int countDirectories() {
        return root.foldMap(0, file -> file.isDirectory() ? 1 : 0, Integer::sum);
    }

    public <T> T foldMap(T seed, FileTreeMapper<T> mapper, FileTreeReducer<T> reduce) {
        return root.foldMap(seed, mapper, reduce);
    }

    public <T> T foldMap(T seed, FileTreeMapper<T> mapper,
                         FileTreeReducer<T> reducer, FileTreeReducer<T> dirReducer) {
        return root.foldMap(seed, mapper, reducer, dirReducer);
    }

    public static FileTree directory(File file) throws IOException {
        return directory(file.toPath());
    }

    public static FileTree directory(Path path) throws IOException {
        return new FileTree(Node.createNode(path));
    }

    public static Option<FileTree> directoryOption(File file) throws IOException {
        if (!file.exists()) {
            return Option.none();
        }
        return Option.of(new FileTree(Node.createNode(file.toPath())));
    }

    public static Option<FileTree> directoryOption(Path path) throws IOException {
        return directoryOption(path.toFile());
    }

    @Override
    public String toString() {
        return "FileTree{" +
                "root=" + root +
                '}';
    }

    public static class Node {
        @Getter
        final Path path;

        Node(Path path) {
            this.path = path;
        }

        public File getFile() {
            return path.toFile();
        }

        public List<Node> getNodes() {
            return List.empty();
        }

        public List<Path> getFiles() {
            return List.empty();
        }

        public boolean isFile() {
            return true;
        }

        public boolean isDirectory() {
            return false;
        }

        <T> T foldMap(T seed, FileTreeMapper<T> mapper, FileTreeReducer<T> reducer) {
            return mapper.apply(this);
        }

        <T> T foldMap(T seed, FileTreeMapper<T> mapper,
                      FileTreeReducer<T> reducer, FileTreeReducer<T> dirReducer) {
            return mapper.apply(this);
        }

        static Node createNode(Path path) throws IOException {
            final File file = path.toFile();
            if (file.isDirectory()) {
                final List<Node> nodes = Files.list(path)
                        .map(unchecked(Node::createNode))
                        .collect(List.collector());
                return new DirectoryNode(path, nodes);
            }
            return new Node(path);
        }

        @Override
        public String toString() {
            return "Node{" +
                    "path=" + path +
                    '}';
        }
    }

    private static class DirectoryNode extends Node {
        final List<Node> nodes;

        DirectoryNode(Path file, List<Node> nodes) {
            super(file);
            this.nodes = nodes;
        }

        @Override
        public List<Node> getNodes() {
            return nodes;
        }

        @Override
        public List<Path> getFiles() {
            return nodes.map(Node::getPath);
        }

        @Override
        public boolean isFile() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        <T> T foldMap(T seed, FileTreeMapper<T> mapper, FileTreeReducer<T> reducer) {
            final T foldedChild = getNodes()
                    .map(node -> node.foldMap(seed, mapper, reducer))
                    .fold(seed, reducer);
            return reducer.apply(mapper.apply(this), foldedChild);
        }

        @Override
        <T> T foldMap(T seed, FileTreeMapper<T> mapper,
                      FileTreeReducer<T> reducer, FileTreeReducer<T> dirReducer) {
            final T foldedChild = getNodes()
                    .map(node -> node.foldMap(seed, mapper, reducer))
                    .fold(seed, reducer);
            return dirReducer.apply(mapper.apply(this), foldedChild);
        }

        @Override
        public String toString() {
            return "DirectoryNode{" +
                    "path=" + path +
                    ", nodes=" + nodes +
                    '}';
        }
    }

}
