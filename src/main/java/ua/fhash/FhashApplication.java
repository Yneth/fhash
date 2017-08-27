package ua.fhash;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.vavr.Function1;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import ua.fhash.ftree.FileTree;
import ua.fhash.ftree.FileTreeMapper;
import ua.fhash.ftree.FileTreeReducer;

import static io.vavr.API.unchecked;

@Slf4j
public class FhashApplication {

    public static void main(String[] args) throws NoSuchAlgorithmException {
        final long startTime = System.currentTimeMillis();

        final int bufferSize = 8096;
        final String algorithm = "SHA-256";
        final String folder = "C:/Users/antonnn/Desktop";
        final Path rootPath = Paths.get(folder);

        Supplier<MessageDigest> digestFactory = unchecked(() -> createMessageDigest(algorithm));
        Function1<File, MessageDigest> digestService = unchecked(file -> digest(bufferSize, digestFactory, file));

        Try.of(() -> FileTree.directoryOption(rootPath.toFile())
                .getOrElseThrow(RuntimeException::new)
        )
                .mapTry(tree -> tree.foldMap(seed1(), mapper1(digestService), reducer1(), dirReducer1()))
                .andThenTry(future -> System.out.println(future.get().mapValues(MessageDigest::digest)))
//                .andThenTry(future -> System.out.println(Arrays.toString(future.get().digest())))
                .onFailure(Throwable::printStackTrace)
                .andFinally(() -> System.out.println(System.currentTimeMillis() - startTime));
    }

    private static CompletableFuture<Map<Path, MessageDigest>> seed1() {
        return CompletableFuture.completedFuture(HashMap.empty());
    }

    private static FileTreeMapper<CompletableFuture<Map<Path, MessageDigest>>> mapper1(
            Function1<File, MessageDigest> digestService) {
        return node -> CompletableFuture.supplyAsync(() ->
                HashMap.of(node.getPath(), digestService.apply(node.getFile())));
    }

    private static FileTreeReducer<CompletableFuture<Map<Path, MessageDigest>>> reducer1() {
        return (f1, f2) -> f1.thenCompose(n1 -> f2.thenApply(n1::merge));
    }

    private static FileTreeReducer<CompletableFuture<Map<Path, MessageDigest>>> dirReducer1() {
        return (f1, f2) -> f1.thenCompose(n1 -> f2.thenApply(n2 -> {
            final FileTreeReducer<MessageDigest> reducer = (d1, d2) -> {
                d1.update(d2.digest());
                return d1;
            };
            final MessageDigest fileHashes = n2.values().reduce(reducer);
            return n1.mapValues(d -> reducer.apply(d, fileHashes)).merge(n2);
        }));
    }

    private static CompletableFuture<MessageDigest> seed(Supplier<MessageDigest> factory) {
        return CompletableFuture.completedFuture(factory.get());
    }

    private static FileTreeReducer<CompletableFuture<MessageDigest>> reducer() {
        return (f1, f2) -> f1.thenCompose(n1 -> f2.thenApply(n2 -> {
            n1.update(n2.digest());
            return n1;
        }));
    }

    private static FileTreeMapper<CompletableFuture<MessageDigest>> mapper(
            Function1<File, MessageDigest> digestService) {
        return node -> CompletableFuture.supplyAsync(() -> digestService.apply(node.getFile()));
    }

    private static MessageDigest createMessageDigest(String algorithm)
            throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(algorithm);
    }

    private static MessageDigest digest(int bufferSize, Supplier<MessageDigest> factory, File file)
            throws IOException, NoSuchAlgorithmException {
        log.debug("Hashing {}", file);

        MessageDigest instance = factory.get();
        if (file.isDirectory()) {
            instance.update(file.getAbsolutePath().getBytes());
            return instance;
        }

        try (FileChannel channel = new FileInputStream(file).getChannel()) {
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);
            while (channel.read(byteBuffer) != -1) {
                byteBuffer.flip();
                instance.update(byteBuffer);
                byteBuffer.compact();
            }
        }
        return instance;
    }

}
