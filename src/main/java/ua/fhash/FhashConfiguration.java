package ua.fhash;

import java.nio.file.Path;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FhashConfiguration {

    private int bufferSize;
    private final Path inputPath;
    private final Path outputPath;
    private final HashAlgorithm algorithm;

}
