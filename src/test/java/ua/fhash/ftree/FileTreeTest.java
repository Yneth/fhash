package ua.fhash.ftree;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.vavr.control.Option;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class FileTreeTest {

    @Test
    void directoryOptionWithExistingFile() throws Exception {
        // Given
        File file = new File("src/test/resources/testFile");

        // When
        Option<FileTree> fileTreeOption = FileTree.directoryOption(file);

        // Then
        assertTrue(fileTreeOption.isDefined());
    }

    @Test
    void directoryOptionWithExistingFolder() throws Exception {
        // Given
        File folder = new File("src/test/resources/testFolder");

        // When
        Option<FileTree> fileTreeOption = FileTree.directoryOption(folder);

        // Then
        assertTrue(fileTreeOption.isDefined());
    }

    @Test
    void directoryOptionWithExistingEmptyFolder() throws Exception {
        // Given
        File emptyFolder = new File("src/test/resources/testEmptyFolder");

        // When
        Option<FileTree> fileTreeOption = FileTree.directoryOption(emptyFolder);

        // Then
        assertTrue(fileTreeOption.isDefined());
    }

    @Test
    void directoryOptionWithNonExistingFile() throws Exception {
        // Given
        Path path = Paths.get("/noSuchFile");

        // When
        Option<FileTree> fileTreeOption = FileTree.directoryOption(path.toFile());

        // Then
        assertTrue(fileTreeOption.isEmpty());
    }

    // TODO: should not be a valid case
    @Test
    void directoryWithNonExistingFile() throws Exception {
        // Given
        Path path = Paths.get("/nonSuchFolder");

        // When
        FileTree directory = FileTree.directory(path.toFile());

        // Then
        assertEquals(1, directory.count());
    }

    @Test
    void directoryWithExistingFolderCorrectNumberOfFiles() throws Exception {
        // Given
        File folder = new File("src/test/resources/testFolder");

        // When
        FileTree directory = FileTree.directory(folder);

        // Then
        assertEquals(3, directory.count());
    }

}