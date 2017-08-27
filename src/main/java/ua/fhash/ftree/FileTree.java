package ua.fhash.ftree;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.vavr.collection.List;
import lombok.Getter;

import static io.vavr.API.unchecked;

public class FileTree {

    private Node root;

    private FileTree(Node root) {
        this.root = root;
    }

    public <T> T foldMap(T seed, FileTreeMapper<T> map, FileTreeReducer<T> reduce) {
        return root.foldMap(seed, map, reduce);
    }

    public static FileTree directory(File file) throws IOException {
        return new FileTree(Node.createNode(file));
    }

    @Override
    public String toString() {
        return "FileTree{" +
                "root=" + root +
                '}';
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

        <T> T foldMap(T seed, FileTreeMapper<T> mapper, FileTreeReducer<T> reducer) {
            return mapper.apply(this);
        }

        static Node createNode(File file) throws IOException {
            if (file.isDirectory()) {
                final List<Node> nodes = Files.list(file.toPath())
                        .map(Path::toFile)
                        .map(unchecked(Node::createNode))
                        .collect(List.collector());
                return new DirectoryNode(file, nodes);
            }
            return new Node(file);
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

        @Override
        public List<Node> getNodes() {
            return nodes;
        }

        @Override
        public List<File> getFiles() {
            return nodes.map(Node::getFile);
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
            T foldedChild = getNodes()
                    .map(node -> node.foldMap(seed, mapper, reducer))
                    .fold(seed, reducer);
            return reducer.apply(mapper.apply(this), foldedChild);
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
