package ua.fhash;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.vavr.CheckedFunction0;
import io.vavr.CheckedFunction1;
import io.vavr.CheckedFunction2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Try;

import static io.vavr.API.unchecked;
import static java.lang.Math.min;
import static java.nio.channels.FileChannel.MapMode.READ_ONLY;

public class FhashApplication {

    public static void main(String[] args) throws NoSuchAlgorithmException {
        final long startTime = System.currentTimeMillis();

        final String folder = "C:/Users/antonnn/Desktop";
        final Path rootPath = Paths.get(folder);

        Try.of(() -> FileTree.directory(rootPath.toFile()))
                .onFailure(Throwable::printStackTrace)
                .mapTry(tree -> tree.mapReduce(nodeMapper(), reducer()))
                .andThen(future -> unchecked((CheckedFunction0<MessageDigest>) future::get));

        System.out.println(System.currentTimeMillis() - startTime);
    }

    private static Map<String, FileTree.Node> createEntry(FileTree.Node node) {
        final File file = node.getFile();
        return HashMap.of(file.getName(), node);
    }

    private static BiFunction<CompletableFuture<MessageDigest>, CompletableFuture<MessageDigest>, CompletableFuture<MessageDigest>>
    reducer() {
        return (f1, f2) -> f1.thenCompose(n1 -> f2.thenApply(n2 -> {
            n1.update(n2.digest());
            return n1;
        }));
    }


    private static Function<FileTree.Node, CompletableFuture<MessageDigest>> nodeMapper() {
        return node -> CompletableFuture.supplyAsync(unchecked(() -> digest(node.getFile())));
    }

    private static MessageDigest createMessageDigest() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }

    private static MessageDigest digest(File file) throws IOException, NoSuchAlgorithmException {
        System.out.println(file.getName());
        int bufferSize = 4096;

        MessageDigest instance = MessageDigest.getInstance("SHA-256");
        System.out.println(file.getName());
        try (FileChannel channel = new FileInputStream(file).getChannel()) {
            final MappedByteBuffer mappedByteBuffer =
                    channel.map(READ_ONLY, 0L, channel.size());
            byte[] buffer = new byte[bufferSize];
            int length;
            while (mappedByteBuffer.hasRemaining()) {
                length = min(mappedByteBuffer.remaining(), bufferSize);
                mappedByteBuffer.get(buffer, 0, length);
                instance.update(buffer, 0, length);
            }
        }
        return instance;
    }

    private static <T> Try<T> walk(Path path, CheckedFunction1<File, T> mapper, CheckedFunction2<T, T, T> reducer) {
        final File file = path.toFile();

        if (file.isFile()) {
            return Try.of(() -> mapper.apply(file));
        } else {
            return Try.of(() -> Files.list(path))
                    .flatMap(ps -> ps.map(p -> walk(p, mapper, reducer))
                            .reduce((l, r) -> map2(l, r, reducer))
                            .orElseThrow(RuntimeException::new)
                    );
        }
    }

    private static <A, R> Try<R> map2(Try<A> left, Try<A> right, CheckedFunction2<A, A, R> reducer) {
        return left.flatMapTry(l -> right.mapTry(r -> reducer.apply(l, r)));
    }

}
