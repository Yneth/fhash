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

import io.vavr.control.Try;
import ua.fhash.ftree.FileTree;
import ua.fhash.ftree.FileTreeMapper;
import ua.fhash.ftree.FileTreeReducer;

import static io.vavr.API.unchecked;

public class FhashApplication {

    public static void main(String[] args) throws NoSuchAlgorithmException {
        final long startTime = System.currentTimeMillis();

        final int bufferSize = 8096;
        final String algorithm = "SHA-256";
        final String folder = "C:/Users/antonnn/Desktop";
        final Path rootPath = Paths.get(folder);

        Supplier<MessageDigest> digestFactory = unchecked(() -> createMessageDigest(algorithm));

        Try.of(() -> FileTree.directory(rootPath.toFile()))
                .mapTry(tree -> tree.foldMap(seed(digestFactory), nodeMapper(bufferSize, digestFactory), reducer()))
                .andThenTry(future -> System.out.println(future.get()))
                .onFailure(Throwable::printStackTrace)
                .andFinally(() -> System.out.println(System.currentTimeMillis() - startTime));
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

    private static FileTreeMapper<CompletableFuture<MessageDigest>> nodeMapper(
            int bufferSize, Supplier<MessageDigest> factory) {
        return node -> CompletableFuture.supplyAsync(
                unchecked(() -> digest(bufferSize, factory, node.getFile()))
        );
    }

    private static MessageDigest createMessageDigest(String algorithm)
            throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(algorithm);
    }

    private static MessageDigest digest(int bufferSize, Supplier<MessageDigest> factory, File file)
            throws IOException, NoSuchAlgorithmException {
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
