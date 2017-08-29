package ua.fhash;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import ua.fhash.fwalk.PathMapper;
import ua.fhash.fwalk.Reducer;

import static io.vavr.API.unchecked;

@Slf4j
public class FhashApplication {

    private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(8);

    public static void main(String[] args) {
        final long startTime = System.currentTimeMillis();

        final int bufferSize = 8096;
        final String algorithm = "SHA-256";
        final String folder = "C:/Users/Anton_Bondarenko/Desktop/test/ctco-fpos";
//        final String folder = "C:/Users/Anton_Bondarenko/Desktop/cmder";
        final Path rootPath = Paths.get(folder);

        Supplier<MessageDigest> digestFactory = unchecked(() -> createMessageDigest(algorithm));
        Function<File, MessageDigest> digestService = unchecked(file -> digest(bufferSize, digestFactory, file));

        Try.of(() -> walk(rootPath, seed(), mapper(digestService), reducer(), dirReducer()))
                .andThenTry(future -> System.out.println(future.get()))
                .onFailure(Throwable::printStackTrace)
                .andFinally(() -> System.out.println((System.currentTimeMillis() - startTime) / 1000.0))
                .andFinally(THREAD_POOL::shutdown);
    }

    private static <T> T walk(Path path, T seed, PathMapper<T> mapper,
                              Reducer<T> reducer, Reducer<T> dirReducer)
            throws IOException {
        final File file = path.toFile();
        if (file.isDirectory()) {
            final T foldedChild = Files.list(path)
                    .sorted(Path::compareTo)
                    .map(unchecked(p -> walk(p, seed, mapper, reducer, dirReducer)))
                    .reduce(seed, reducer::apply);
            return dirReducer.apply(mapper.apply(path), foldedChild);
        }
        return mapper.apply(path);
    }

    private static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, THREAD_POOL);
    }

    private static CompletableFuture<Map<Path, MessageDigest>> seed() {
        return CompletableFuture.completedFuture(HashMap.empty());
    }

    private static PathMapper<CompletableFuture<Map<Path, MessageDigest>>> mapper(
            Function<File, MessageDigest> digestService) {
        return path -> supplyAsync(() -> HashMap.of(path, digestService.apply(path.toFile())));
    }

    private static Reducer<CompletableFuture<Map<Path, MessageDigest>>> reducer() {
        return (f1, f2) -> f1.thenCompose(n1 -> f2.thenApply(n1::merge));
    }

    private static Reducer<CompletableFuture<Map<Path, MessageDigest>>> dirReducer() {
        return (f1, f2) -> f1.thenCompose(n1 -> f2.thenApply(n2 -> {
            final Tuple2<Path, MessageDigest> dir = n1.head();
            final MessageDigest dirHash = n2.values()
                    .fold(dir._2, FhashApplication::combineDigest);
            return n1.put(dir._1, dirHash).merge(n2);
        }));
    }

    private static MessageDigest combineDigest(MessageDigest m1, MessageDigest m2) {
        m1.update(m2.digest());
        return m1;
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
