package ulid4j;

import com.github.f4b6a3.ulid.UlidCreator;
import com.github.f4b6a3.ulid.creator.UlidSpecCreator;
import de.huxhorn.sulky.ulid.ULID;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author shamil
 */
@State(Scope.Thread)
public class UlidBenchmark {
    private final Ulid ulid4j = new Ulid();
    private final ULID sulkyULID = new ULID();
    private final UlidSpecCreator ulidCreator = UlidCreator.getUlidSpecCreator();

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public String ulid4jThroughput() {
        return ulid4j.next();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public String ulid4jAverage() {
        return ulid4j.next();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public String sulkyULIDThroughput() {
        return sulkyULID.nextULID();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public String sulkyULIDAverage() {
        return sulkyULID.nextULID();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public String ULIDSpecCreatorThroughput() {
        return ulidCreator.createString();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public String ULIDSpecCreatorAverage() {
        return ulidCreator.createString();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public String jdkUUIDStringThroughput() {
        return UUID.randomUUID().toString();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public String jdkUUIDStringAverage() {
        return UUID.randomUUID().toString();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(UlidBenchmark.class.getSimpleName())
                .warmupIterations(5)
                .measurementIterations(5)
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
