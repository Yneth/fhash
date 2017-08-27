package ua.fhash.ftree;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.vavr.Function1;
import io.vavr.collection.List;
import lombok.Getter;

import static io.vavr.API.unchecked;

public class FileTree {

    private Node root;

    private FileTree(Node root) {
        this.root = root;
    }

    public List<Node> currentFolder() {
        return root.getNodes();
    }

    public <T> T mapReduce(Function<Node, T> mapper, BiFunction<T, T, T> reducer) {
        return root.mapReduce(mapper, reducer);
    }

    public <T> T foldMap(T seed, Function1<Node, T> map, BiFunction<T, T, T> reduce) {
        return root.foldMap(seed, map, reduce);
    }

    public static FileTree directory(File file) throws IOException {
        return new FileTree(createNode(file));
    }

    @Override
    public String toString() {
        return "FileTree{" +
                "root=" + root +
                '}';
    }

    private static Node createNode(File file) throws IOException {
        if (file.isDirectory()) {
            final List<Node> nodes = Files.list(file.toPath())
                    .map(Path::toFile)
                    .map(unchecked(FileTree::createNode))
                    .collect(List.collector());
            return new DirectoryNode(file, nodes);
        }
        return new Node(file);
    }

    public static class Node {
        @Getter
        final File file;

        Node(File file) {
            this.file = file;
        }

        public List<Node> getNodes() {
            return List.empty();
        }

        public List<File> getFiles() {
            return List.empty();
        }

        public boolean isFile() {
            return true;
        }

        public boolean isDirectory() {
            return false;
        }

        public <T> T mapReduce(Function<Node, T> mapper, BiFunction<T, T, T> reducer) {
            return mapper.apply(this);
        }

        <T> T foldMap(T seed, Function1<Node, T> map, BiFunction<T, T, T> reduce) {
            return this.mapReduce(map, reduce);
        }

        @Override
        public String toString() {
            return "Node{" +
                    "file=" + file +
                    '}';
        }
    }

    public static class DirectoryNode extends Node {
        final List<Node> nodes;

        DirectoryNode(File file, List<Node> nodes) {
            super(file);
            this.nodes = nodes;
        }

        public List<Node> getNodes() {
            return nodes;
        }

        public List<File> getFiles() {
            return nodes.map(Node::getFile);
        }

        public boolean isFile() {
            return false;
        }

        public boolean isDirectory() {
            return true;
        }

        public <T> T mapReduce(Function<Node, T> mapper, BiFunction<T, T, T> reducer) {
            T reducedChild = getNodes()
                    .map(node -> node.mapReduce(mapper, reducer))
                    .reduce(reducer);
            return reducer.apply(mapper.apply(this), reducedChild);
        }

        <T> T foldMap(T seed, Function1<Node, T> map, BiFunction<T, T, T> reduce) {
            return reduce.apply(seed, this.mapReduce(map, reduce));
        }

        @Override
        public String toString() {
            return "DirectoryNode{" +
                    "file=" + file +
                    ", nodes=" + nodes +
                    '}';
        }
    }

}
