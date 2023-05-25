package silenceremover.cli.provider.builtin;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import silenceremover.SilenceRemover;
import silenceremover.cli.SilenceRemoverCli;
import silenceremover.cli.provider.CliCommandProvider;
import silenceremover.config.ProjectConfig;

public class ProcessCommandProvider implements CliCommandProvider {
	private static final String commandName = "process";
	private final ProcessCommand command = new ProcessCommand();

	@Parameters(commandNames = {commandName})
	class ProcessCommand {
		@Parameter(names = {BuiltinCliParameters.INPUT_FILE}, required = true)
		Path inputFile;

		@Parameter(names = {BuiltinCliParameters.OUTPUT_FILE}, required = true)
		Path outputFile;

		// TODO: Add missing parameters (see BuiltinCliParameters.java)
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

		SilenceRemover silenceRemover = new SilenceRemover();
		ProjectConfig config = ProjectConfig.builder(command.inputFile, command.outputFile)
				// TODO: Add values from not yet implemented parameters
				.build();
		AtomicInteger lastPrintedPercentage = new AtomicInteger(-1);

		silenceRemover.process(config, (progress) -> {
			int percentage = (int) (progress * 100);

			if (percentage != lastPrintedPercentage.get() && percentage % 10 == 0) {
				SilenceRemoverCli.LOGGER.info("{}% ... ", percentage);
				lastPrintedPercentage.set(percentage);
			}
		});
		SilenceRemoverCli.LOGGER.info("Done!");
	}

	private void validateArgs() {
		if (!command.inputFile.toFile().exists()) {
			throw new IllegalArgumentException("Passed input file doesn't exist!");
		}

		if (command.outputFile.toFile().exists()) {
			throw new IllegalArgumentException("Output file already exist!");
		}
	}
}