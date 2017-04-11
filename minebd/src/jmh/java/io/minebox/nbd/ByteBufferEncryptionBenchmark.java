package io.minebox.nbd;

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
//        bitPatternGenerator.createDeterministicPattern1(123123);
    }

    @Setup
    public void setup(){
        bitPatternGenerator = new BitPatternGenerator("secret");

    }
    @Benchmark
    public void testBitPattern1() {
        bitPatternGenerator.createDeterministicPattern1(123123);
    }

    @Benchmark
    public void testBitPattern2() {
        bitPatternGenerator.createDeterministicPattern2(123123);
    }
}
