package silenceremover;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

import silenceremover.config.ProjectConfig;

public class SplitThread implements Callable<List<Path>> {
	private final ProjectConfig config;
	private final List<Interval> intervals;
	private final Function<Interval, Path> tempFileGenerator;

	public SplitThread(ProjectConfig config, List<Interval> intervals, Function<Interval, Path> tempFileGenerator) {
		this.config = config;
		this.tempFileGenerator = tempFileGenerator;
		this.intervals = intervals;
	}

	@Override
	public List<Path> call() {
		boolean applyFilter = true;
		double minimumIntervalDuration = config.minSegmentLength;
		List<Path> outputPaths = new ArrayList<>(intervals.size());

		for (Interval interval : intervals) {
			outputPaths.add(tempFileGenerator.apply(interval));
		}

		String[] command = generateCommand(outputPaths, applyFilter, minimumIntervalDuration);
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true);
		Process process;

		try {
			process = processBuilder.start();

			// Kill FFmpeg instance when user exits application
			Runtime.getRuntime().addShutdownHook(new Thread(() -> Util.killProcess(process)));

			BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;

			while ((line = stdOut.readLine()) != null) {
				SilenceRemover.LOGGER.trace(line.toString());
			}

			int exitCode = process.waitFor();

			if (exitCode != 0) {
				throw new RuntimeException("Error while splitting the video");
			}
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}

		return outputPaths;
	}

	private String[] generateCommand(List<Path> outputFiles, boolean applyFilter, double minimumIntervalDuration) {
		List<String> commandParts = new ArrayList<>();
		commandParts.add(config.ffmpegExecutable.toAbsolutePath().toString());

		for (Interval interval : intervals) {
			// "threads" is needed both before each input file and after:
			// https://github.com/cisco/openh264/issues/1889#issuecomment-91018851
			commandParts.add("-threads");
			commandParts.add(Integer.toString(config.threadsPerSegment));
			commandParts.add("-i");
			commandParts.add(config.inputFile.toString());
		}

		for (int i = 0; i < intervals.size(); i++) {
			Interval interval = intervals.get(i);
			Path outputFile = outputFiles.get(i);

			commandParts.add("-map");
			commandParts.add(Integer.toString(i));
			commandParts.add("-ss");
			commandParts.add(Double.toString(interval.getStart()));
			commandParts.add("-to");
			commandParts.add(Double.toString(interval.getEnd()));
			commandParts.add("-vsync");
			commandParts.add("1");
			commandParts.add("-async");
			commandParts.add("1");
			commandParts.add("-safe");
			commandParts.add("0");
			commandParts.add("-threads");
			commandParts.add(Integer.toString(config.threadsPerSegment));
			commandParts.add("-ignore_unknown");
			commandParts.add("-y");

			if (config.audioOnly) {
				commandParts.add("-vn");
			}

			commandParts.add(outputFile.toString());
		}

		return commandParts.toArray(String[]::new);
	}
}
