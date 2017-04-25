package io.minebox.nbd;

import io.minebox.nbd.encryption.BitPatternGenerator;
import io.minebox.nbd.encryption.BitPatternGeneratorTest;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Created by andreas on 11.04.17.
 */
@State(value = Scope.Benchmark)
public class ByteBufferEncryptionBenchmark {
    private static ByteBufferEncryptionTest t = new ByteBufferEncryptionTest();
    private BitPatternGenerator bitPatternGenerator;

    final BitPatternGeneratorTest test = new BitPatternGeneratorTest();

    @Benchmark
    public void testSecureRandom() throws Exception {
        test.testSecureRandom();
    }

    @Benchmark
    public void testGuava() throws Exception {
        test.testGuava();
    }

    @Benchmark
    public void testDigest() throws Exception {
        test.testMD();
    }
}
