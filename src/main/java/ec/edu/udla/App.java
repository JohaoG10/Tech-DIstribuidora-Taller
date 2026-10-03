package ec.edu.udla;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.main.Main;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;

/** Aplicación standalone. Python en Colab solo prepara y controla este proceso. */
public final class App {
    public static void main(String[] args) throws Exception {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Use --input=RUTA --output=RUTA --logs=RUTA --validate=true|false");
            }
            String[] pair = arg.substring(2).split("=", 2);
            options.put(pair[0], pair[1]);
        }
        Path input = Path.of(options.getOrDefault("input", "input")).toAbsolutePath().normalize();
        Path output = Path.of(options.getOrDefault("output", "output")).toAbsolutePath().normalize();
        Path logs = Path.of(options.getOrDefault("logs", "logs")).toAbsolutePath().normalize();
        if (input.equals(output) || output.startsWith(input) || input.startsWith(output)) {
            throw new IllegalArgumentException("input y output deben ser carpetas diferentes sin anidamiento");
        }
        for (Path dir : new Path[]{input, output, logs}) Files.createDirectories(dir);
        boolean validate = Boolean.parseBoolean(options.getOrDefault("validate", "false"));
        Main main = new Main();
        main.bind("processedFiles", FileIdempotentRepository.fileIdempotentRepository(
            logs.resolve("processed-files.dat").toFile(), 10000));
        main.configure().addRoutesBuilder(new FileTransferRoute(input, output, logs, validate));
        System.out.println("INPUT=" + input + " OUTPUT=" + output + " VALIDATE=" + validate);
        main.run();
    }
}
