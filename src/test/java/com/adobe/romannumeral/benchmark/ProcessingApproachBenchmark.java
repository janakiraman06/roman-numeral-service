package com.adobe.romannumeral.benchmark;

import com.adobe.romannumeral.converter.RomanNumeralConverter;
import com.adobe.romannumeral.converter.StandardRomanNumeralConverter;
import com.adobe.romannumeral.model.ConversionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

/**
 * Benchmark comparing different processing approaches for range conversions.
 * 
 * This is NOT a JMH benchmark (would need proper warmup, iterations, etc.)
 * but provides a quick comparison for understanding relative performance.
 * 
 * Run with: mvn test -Dtest=ProcessingApproachBenchmark
 */
public class ProcessingApproachBenchmark {

    private static RomanNumeralConverter converter;
    
    public static void main(String[] args) {
        // Initialize converter
        converter = new StandardRomanNumeralConverter();
        ((StandardRomanNumeralConverter) converter).initialize();
        
        System.out.println("=".repeat(70));
        System.out.println("Processing Approach Benchmark");
        System.out.println("=".repeat(70));
        System.out.println();
        
        int[] rangeSizes = {50, 100, 250, 500, 1000};
        int iterations = 100;
        
        for (int rangeSize : rangeSizes) {
            System.out.println("Range Size: " + rangeSize + " items");
            System.out.println("-".repeat(50));
            
            // Warmup
            for (int i = 0; i < 10; i++) {
                processWithForLoop(1, rangeSize);
            }
            
            // Benchmark each approach
            long forLoopTime = benchmarkForLoop(rangeSize, iterations);
            long sequentialStreamTime = benchmarkSequentialStream(rangeSize, iterations);
            long parallelStreamTime = benchmarkParallelStream(rangeSize, iterations);
            long completableFutureTime = benchmarkCompletableFuture(rangeSize, iterations);
            long virtualThreadTime = benchmarkVirtualThreads(rangeSize, iterations);
            
            // Print results
            System.out.printf("  %-25s %8d ns  (baseline)%n", "For Loop:", forLoopTime);
            System.out.printf("  %-25s %8d ns  (%.1fx slower)%n", "Sequential Stream:", 
                sequentialStreamTime, (double)sequentialStreamTime/forLoopTime);
            System.out.printf("  %-25s %8d ns  (%.1fx slower)%n", "Parallel Stream:", 
                parallelStreamTime, (double)parallelStreamTime/forLoopTime);
            System.out.printf("  %-25s %8d ns  (%.1fx slower)%n", "CompletableFuture:", 
                completableFutureTime, (double)completableFutureTime/forLoopTime);
            System.out.printf("  %-25s %8d ns  (%.1fx slower)%n", "Virtual Threads:", 
                virtualThreadTime, (double)virtualThreadTime/forLoopTime);
            
            System.out.println();
        }
        
        System.out.println("=".repeat(70));
        System.out.println("CONCLUSION:");
        System.out.println("For CPU-bound cache lookups, simple for-loop is fastest.");
        System.out.println("Parallelism overhead exceeds any benefit for O(1) operations.");
        System.out.println("=".repeat(70));
    }
    
    // ==================== APPROACH 1: Simple For Loop ====================
    private static List<ConversionResult> processWithForLoop(int min, int max) {
        List<ConversionResult> results = new ArrayList<>(max - min + 1);
        for (int i = min; i <= max; i++) {
            results.add(ConversionResult.of(i, converter.convert(i)));
        }
        return results;
    }
    
    private static long benchmarkForLoop(int rangeSize, int iterations) {
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            processWithForLoop(1, rangeSize);
            total += System.nanoTime() - start;
        }
        return total / iterations;
    }
    
    // ==================== APPROACH 2: Sequential Stream ====================
    private static List<ConversionResult> processWithSequentialStream(int min, int max) {
        return IntStream.rangeClosed(min, max)
            .mapToObj(n -> ConversionResult.of(n, converter.convert(n)))
            .toList();
    }
    
    private static long benchmarkSequentialStream(int rangeSize, int iterations) {
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            processWithSequentialStream(1, rangeSize);
            total += System.nanoTime() - start;
        }
        return total / iterations;
    }
    
    // ==================== APPROACH 3: Parallel Stream ====================
    private static List<ConversionResult> processWithParallelStream(int min, int max) {
        return IntStream.rangeClosed(min, max)
            .parallel()
            .mapToObj(n -> ConversionResult.of(n, converter.convert(n)))
            .sorted(Comparator.comparingInt(r -> Integer.parseInt(r.input())))
            .toList();
    }
    
    private static long benchmarkParallelStream(int rangeSize, int iterations) {
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            processWithParallelStream(1, rangeSize);
            total += System.nanoTime() - start;
        }
        return total / iterations;
    }
    
    // ==================== APPROACH 4: CompletableFuture ====================
    private static List<ConversionResult> processWithCompletableFuture(int min, int max) {
        List<CompletableFuture<ConversionResult>> futures = IntStream.rangeClosed(min, max)
            .mapToObj(n -> CompletableFuture.supplyAsync(() -> 
                ConversionResult.of(n, converter.convert(n))))
            .toList();
        
        return futures.stream()
            .map(CompletableFuture::join)
            .sorted(Comparator.comparingInt(r -> Integer.parseInt(r.input())))
            .toList();
    }
    
    private static long benchmarkCompletableFuture(int rangeSize, int iterations) {
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            processWithCompletableFuture(1, rangeSize);
            total += System.nanoTime() - start;
        }
        return total / iterations;
    }
    
    // ==================== APPROACH 5: Virtual Threads ====================
    private static List<ConversionResult> processWithVirtualThreads(int min, int max) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ConversionResult>> futures = IntStream.rangeClosed(min, max)
                .mapToObj(n -> executor.submit(() -> 
                    ConversionResult.of(n, converter.convert(n))))
                .toList();
            
            return futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .sorted(Comparator.comparingInt(r -> Integer.parseInt(r.input())))
                .toList();
        }
    }
    
    private static long benchmarkVirtualThreads(int rangeSize, int iterations) {
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            processWithVirtualThreads(1, rangeSize);
            total += System.nanoTime() - start;
        }
        return total / iterations;
    }
}

