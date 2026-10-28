package me.eldodebug.soar.management.misc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import me.eldodebug.soar.logger.GlideLogger;

/**
 * Controls the external click-tripler helper.
 *
 * The helper has to run as root (uinput plus raw input access), so the client must not try to
 * start or stop it. Instead this writes the small state file the helper polls, which works
 * without privileges because the file is created by the game's user and only read by root.
 */
public class ClickTripler {

	private static final String DEFAULT_STATE_PATH = "/tmp/click-tripler.state";
	private static final long RUNNING_CACHE_MS = 2_000L;

	private static final Path statePath = buildStatePath();

	private static Boolean lastEnabled;
	private static int lastMinDelay = -1;
	private static int lastMaxDelay = -1;

	private static boolean running;
	private static long lastRunningCheck;

	private ClickTripler() {
	}

	private static Path buildStatePath() {

		String override = System.getProperty("glide.clicktripler.state");

		return Paths.get(override != null && !override.isEmpty() ? override : DEFAULT_STATE_PATH);
	}

	public static boolean isSupported() {
		
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
		
		// "Linux" does not contain "nix", so both spellings have to be checked.
		return os.contains("linux") || os.contains("nix") || os.contains("bsd");
	}

	public static Path getStatePath() {
		return statePath;
	}

	/**
	 * Best-effort start command for the setup card. The helper is deliberately not started by
	 * the client, but showing the exact command removes the "what do I do with this?" moment.
	 */
	public static String getSuggestedCommand() {

		String home = System.getProperty("user.home", "");

		String[] candidates = new String[] {
				new File(home, "click-tripler/target/release/click-tripler").getPath(),
				new File(home, ".local/bin/click-tripler").getPath(),
				new File(home, ".cargo/bin/click-tripler").getPath(),
				"/usr/local/bin/click-tripler",
				"/usr/bin/click-tripler"
		};

		String helper = "click-tripler";

		for (String candidate : candidates) {
			if (new File(candidate).isFile()) {
				helper = candidate;
				break;
			}
		}

		return "sudo " + helper + " " + findMouseDevice() + " 40";
	}

	private static String findMouseDevice() {

		File byId = new File("/dev/input/by-id");

		File[] entries = byId.listFiles();

		if (entries != null) {

			String fallback = null;

			for (File entry : entries) {

				String name = entry.getName().toLowerCase(Locale.ENGLISH);

				if (!name.endsWith("-event-mouse")) {
					continue;
				}

				String path = entry.getPath();

				try {
					path = entry.toPath().toRealPath().toString();
				} catch (IOException ignored) {
					// Keep the by-id symlink; it works just as well.
				}

				if (name.contains("g502")) {
					return path;
				}

				if (fallback == null) {
					fallback = path;
				}
			}

			if (fallback != null) {
				return fallback;
			}
		}

		return "/dev/input/eventX";
	}

	/**
	 * Writes the state file if anything changed. Safe to call every frame.
	 */
	public static void sync(boolean enabled, int minDelayMs, int maxDelayMs) {

		if (!isSupported()) {
			return;
		}

		int min = Math.max(1, Math.min(1_000, minDelayMs));
		int max = Math.max(min, Math.min(1_000, maxDelayMs));

		if (lastEnabled != null && lastEnabled == enabled && lastMinDelay == min && lastMaxDelay == max) {
			return;
		}

		String content = "# Written by Glide Client. The click-tripler helper reads this file.\n"
				+ "enabled=" + (enabled ? 1 : 0) + "\n"
				+ "min_delay_ms=" + min + "\n"
				+ "max_delay_ms=" + max + "\n";

		try {
			Files.write(statePath, content.getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

			lastEnabled = enabled;
			lastMinDelay = min;
			lastMaxDelay = max;
		} catch (IOException e) {
			GlideLogger.error("Failed to write the click-tripler state file", e);
		}
	}

	/**
	 * Whether the helper process is currently alive. Cached briefly, because it walks /proc.
	 */
	public static boolean isRunning() {

		long now = System.currentTimeMillis();

		if (now - lastRunningCheck < RUNNING_CACHE_MS) {
			return running;
		}

		lastRunningCheck = now;
		running = scanForProcess();

		return running;
	}

	private static boolean scanForProcess() {

		File[] entries = new File("/proc").listFiles();

		if (entries == null) {
			return false;
		}

		for (File entry : entries) {

			if (!entry.getName().chars().allMatch(Character::isDigit)) {
				continue;
			}

			try {
				byte[] raw = Files.readAllBytes(new File(entry, "cmdline").toPath());
				String commandLine = new String(raw, StandardCharsets.UTF_8).replace('\0', ' ');

				if (commandLine.contains("click-tripler") && !commandLine.contains("GlideClient")) {
					return true;
				}
			} catch (Exception ignored) {
				// Other users' processes are not readable; that is expected.
			}
		}

		return false;
	}
}
