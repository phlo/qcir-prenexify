package at.jku.fmv.qbf.benchmark.time;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;

import at.jku.fmv.qbf.benchmark.TestSet;
import at.jku.fmv.qbf.benchmark.time.Benchmarks.Variables;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(value = 1)
public class QBFstreamVariables {

	@Benchmark
	public void streamVariablesParallel(Variables v, Blackhole hole) {
		v.formula.streamVariables()
			.unordered()
			.parallel()
			.forEach(o -> hole.consume(o));
	}

	public static void main(String[] args) throws Exception {

		String benchmark = "streamVariables";

		TestSet testset = new TestSet(
			Paths.get(TestSet.properties.getProperty("qcir_non-prenex")));
//			Paths.get(TestSet.properties.getProperty("qbf_eval17")));

		Options opt = Benchmarks.getOptions(benchmark, testset)
			.include(
				QBFstreamVariables.class.getName()
				+ "."
				+ benchmark
				+ "*")
			.param("parse", "true")
			.build();
		new Runner(opt).run();
	}
}