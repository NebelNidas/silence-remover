package silenceremover.cli.provider.builtin;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import job4j.Job;

import silenceremover.SilenceRemover;
import silenceremover.cli.SilenceRemoverCli;
import silenceremover.cli.provider.CliCommandProvider;
import silenceremover.config.ProjectConfig;

/**
 * Provides the default {@code process} command.
 */
public class ProcessCommandProvider implements CliCommandProvider {
	private static final String commandName = "process";
	private final ProcessCommand command = new ProcessCommand();

	@Parameters(commandNames = {commandName})
	class ProcessCommand {
		@Parameter(names = {BuiltinCliParameters.INPUT_FILE}, required = true)
		Path inputFile;

		@Parameter(names = {BuiltinCliParameters.OUTPUT_FILE}, required = true)
		Path outputFile;

		@Parameter(names = {BuiltinCliParameters.FFMPEG_EXECUTABLE}, required = true)
		Path ffmpegExecutable;

		@Parameter(names = {BuiltinCliParameters.MIN_SEGMENT_LENGTH})
		Double minSegmentLength;

		@Parameter(names = {BuiltinCliParameters.MAX_NEGATIVE_VOLUME_DEVIATION})
		Integer maxNegVolumeDeviation;

		@Parameter(names = {BuiltinCliParameters.AUDIBLE_SEGMENT_PADDING})
		Double audibleSegmentPadding;

		@Parameter(names = {BuiltinCliParameters.AUDIO_ONLY})
		Boolean audioOnly;

		@Parameter(names = {BuiltinCliParameters.MAX_THREADS})
		Integer maxThreads;

		@Parameter(names = {BuiltinCliParameters.THREADS_PER_SEGMENT})
		Integer threadsPerSegment;
		@Parameter(names = {BuiltinCliParameters.SEGMENTS_PER_FFMPEG_INSTANCE})
		Integer segmentsPerFfmpegInstance;
	}

	@Override
	public String getCommandName() {
		return commandName;
	}

	@Override
	public Object getDataHolder() {
		return command;
	}

	@Override
	public void processArgs() {
		validateArgs();

		ProjectConfig config = ProjectConfig.builder(command.inputFile, command.outputFile, command.ffmpegExecutable)
				.minSegmentLength(command.minSegmentLength)
				.maxNegativeVolumeDeviation(command.maxNegVolumeDeviation)
				.audibleSegmentPadding(command.audibleSegmentPadding)
				.audioOnly(command.audioOnly)
				.maxThreads(command.maxThreads)
				.segmentsPerFfmpegInstance(command.segmentsPerFfmpegInstance)
				.threadsPerSegment(command.threadsPerSegment)
				.build();
		SilenceRemover silenceRemover = new SilenceRemover(config);
		AtomicInteger lastPrintedPercentage = new AtomicInteger(-1);

		Job<?> job = silenceRemover.process();
		job.addProgressListener((progress) -> {
			int percentage = (int) (progress * 100);

			if (Math.abs(percentage - lastPrintedPercentage.get()) > 5) {
				SilenceRemoverCli.LOGGER.info("{}% ... ", percentage);
				lastPrintedPercentage.set(percentage);
			}
		});
		job.runAndAwait();
		SilenceRemoverCli.LOGGER.info("Done!");
	}

	private void validateArgs() {
		if (!command.inputFile.toFile().exists()) {
			throw new IllegalArgumentException("Passed input file doesn't exist!");
		}

		if (command.outputFile.toFile().exists()) {
			throw new IllegalArgumentException("Output file already exist!");
		}

		if (!command.ffmpegExecutable.toFile().exists()) {
			throw new IllegalArgumentException("Passed FFmpeg executable doesn't exist!");
		}
	}
}
