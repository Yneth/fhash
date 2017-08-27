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

import io.vavr.control.Try;
import ua.fhash.ftree.FileTree;
import ua.fhash.ftree.FileTreeMapper;
import ua.fhash.ftree.FileTreeReducer;

import static io.vavr.API.unchecked;

public class FhashApplication {

    public static void main(String[] args) throws NoSuchAlgorithmException {
        final long startTime = System.currentTimeMillis();

        final String folder = "C:/Users/antonnn/Desktop";
        final Path rootPath = Paths.get(folder);

        Try.of(() -> FileTree.directory(rootPath.toFile()))
                .mapTry(tree -> tree.foldMap(seed(), nodeMapper(), reducer()))
                .andThenTry(future -> System.out.println(future.get()))
                .onFailure(Throwable::printStackTrace)
                .andFinally(() -> System.out.println(System.currentTimeMillis() - startTime));
    }

    private static CompletableFuture<MessageDigest> seed()
            throws NoSuchAlgorithmException {
        return CompletableFuture.completedFuture(createMessageDigest());
    }

    private static FileTreeReducer<CompletableFuture<MessageDigest>> reducer() {
        return (f1, f2) -> f1.thenCompose(n1 -> f2.thenApply(n2 -> {
            n1.update(n2.digest());
            return n1;
        }));
    }

    private static FileTreeMapper<CompletableFuture<MessageDigest>> nodeMapper() {
        return node -> CompletableFuture.supplyAsync(unchecked(() -> digest(node.getFile())));
    }

    private static MessageDigest createMessageDigest()
            throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }

    private static MessageDigest digest(File file)
            throws IOException, NoSuchAlgorithmException {
        final int bufferSize = 8096;
        MessageDigest instance = createMessageDigest();
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
