package ua.fhash;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FhashConfiguration {

    private final String algorithm;
    private final String inputPath;
    private final String outputPath;

}
