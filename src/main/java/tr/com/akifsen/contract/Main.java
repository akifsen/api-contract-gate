package tr.com.akifsen.contract;

import java.nio.file.*;
import java.time.Clock;
import java.util.List;

public final class Main {
    public static void main(String[] args) {
        int exit = 2;
        if (args.length < 3 || args.length > 4) {
            System.err.println("Usage: <old.json> <new.json> <output-directory> [waivers.json]");
            System.exit(2);
        }
        boolean outputSafe = false;
        try {
            // Validate both output names before comparison and before any error report is written.
            Files.createDirectories(Path.of(args[2]));
            Path directory = Path.of(args[2]).toRealPath();
            for (String name : List.of("report.json", "report.md")) {
                Path output = directory.resolve(name);
                for (int index : args.length == 4 ? new int[] {0, 1, 3} : new int[] {0, 1}) {
                    Path input = Path.of(args[index]).toAbsolutePath().normalize();
                    if (output.equals(input)
                            || (Files.exists(output) && Files.exists(input) && Files.isSameFile(output, input)))
                        throw new IllegalArgumentException("Report output would overwrite an input file");
                }
            }
            outputSafe = true;
            var report = Gate.compare(
                    Path.of(args[0]), Path.of(args[1]), args.length == 4 ? Path.of(args[3]) : null, Clock.systemUTC());
            Gate.write(report, Path.of(args[2]));
            exit = report.exitCode();
            System.out.println(report.status());
        } catch (Exception e) {
            System.err.println("Contract gate failed: "
                    + Gate.safe(e.getMessage() == null ? "invalid input or output" : e.getMessage()));
            try {
                if (!outputSafe) throw new IllegalStateException("Unsafe output path; error report suppressed");
                Gate.write(
                        new Gate.Report(
                                "ERROR",
                                2,
                                "",
                                "",
                                List.of(),
                                List.of("Invalid input, policy, parser or output; this is not a compatibility pass.")),
                        Path.of(args[2]));
            } catch (Exception ignored) {
                System.err.println("Unable to write error report");
            }
        }
        System.exit(exit);
    }
}
