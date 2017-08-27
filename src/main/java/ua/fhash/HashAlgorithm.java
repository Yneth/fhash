package ua.fhash;

import lombok.Getter;

@Getter
public enum HashAlgorithm {

    SHA256("SHA-256");

    private final String name;

    HashAlgorithm(String name) {
        this.name = name;
    }

}
