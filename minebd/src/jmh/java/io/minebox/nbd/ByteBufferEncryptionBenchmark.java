package io.minebox.nbd;

import io.minebox.nbd.encryption.BitPatternGenerator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Created by andreas on 11.04.17.
 */
@State(value = Scope.Benchmark)
public class ByteBufferEncryptionBenchmark {
    private static ByteBufferEncryptionTest t = new ByteBufferEncryptionTest();
    private BitPatternGenerator bitPatternGenerator;

    @Benchmark
    public void testXor() {

        t.testSimpleXor();
    }

    @Setup
    public void setup() {
        bitPatternGenerator = new BitPatternGenerator("secret");

    }

    @Benchmark
    public void testBitPattern4096() {
        bitPatternGenerator.createDeterministicPattern(123123);
    }

}
