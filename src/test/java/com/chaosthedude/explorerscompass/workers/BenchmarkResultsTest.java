package com.chaosthedude.explorerscompass.workers;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkResultsTest {
    @TempDir Path directory;
    private Object[] row(String variant) {
        return new Object[]{"2026-11-04T12:00:00Z",variant,"id","test:structure","worker","found",
                -100,200,300,400,447.2,45.6,10,8,2,30.1,6};
    }
    @Test void appendsAcrossWritesWithOneHeaderAndSeparateVariantFiles() throws Exception {
        Path baseline=directory.resolve("logs/baseline.csv");
        Path nearest=directory.resolve("logs/nearest-first.csv");
        BenchmarkResults.append(baseline,row("baseline"));
        BenchmarkResults.append(nearest,row("nearest-first"));
        BenchmarkResults.append(baseline,row("baseline"));
        assertEquals(3,Files.readAllLines(baseline).size());
        assertEquals(2,Files.readAllLines(nearest).size());
        assertTrue(Files.readString(baseline).startsWith("timestamp,variant,"));
        assertFalse(Files.readString(baseline).contains("nearest-first"));
    }
    @Test void preservesCsvQuotingAndMissingResults() throws Exception {
        Path file=directory.resolve("results.csv");
        Object[] fields=row("baseline"); fields[3]="a,\"b\""; fields[8]=null;
        BenchmarkResults.append(file,fields);
        assertTrue(Files.readString(file).contains("\"a,\"\"b\"\"\""));
        assertTrue(Files.readString(file).contains(",-100,200,,400,"));
    }
}
