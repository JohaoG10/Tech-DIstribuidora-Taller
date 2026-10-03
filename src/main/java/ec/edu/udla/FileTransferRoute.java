package ec.edu.udla;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** File Transfer con conservación del original, filtro e historial persistente. */
public final class FileTransferRoute extends RouteBuilder {
    private final Path input, output, logs;
    private final boolean validate;
    public FileTransferRoute(Path input, Path output, Path logs, boolean validate) {
        this.input = input; this.output = output; this.logs = logs; this.validate = validate;
    }
    private static String uriPath(Path path) { return path.toString().replace('\\', '/'); }
    @Override public void configure() {
        // CSV inválido: registrar rechazo y conservar el original para su corrección.
        onException(IllegalArgumentException.class).maximumRedeliveries(0).handled(true)
            .process(e -> {
                Throwable error = e.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                audit(e, "ERROR_VALIDACION", error.getMessage(), logs.resolve("errores.csv"));
            })
            .log(LoggingLevel.WARN, "Rechazado ${header.CamelFileName}: ${exception.message}");
        // Fallo técnico: reintentar, registrar y dejar el intercambio fallido.
        onException(Exception.class).maximumRedeliveries(3).redeliveryDelay(1000).handled(false)
            .process(e -> {
                Throwable error = e.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                audit(e, "ERROR_TECNICO", error.toString(), logs.resolve("errores.csv"));
            });
        from("file:" + uriPath(input)
            + "?include=.*[.]csv&noop=true&delay=2000&initialDelay=0"
            + "&readLock=changed&readLockCheckInterval=1000&readLockTimeout=10000&readLockMinLength=0"
            + "&idempotentRepository=#processedFiles"
            + "&idempotentKey=${file:absolute.path}-${file:size}-${file:modified}")
            .routeId("transferencia-pedidos-csv")
            .process(e -> { if (validate) validateOrders(e); })
            .to("file:" + uriPath(output) + "?fileExist=Override&tempFileName=.${file:name}.part")
            .process(e -> audit(e, "COPIADO", "Archivo transferido sin transformar su contenido", logs.resolve("transferencias.csv")))
            .log("COPIADO ${header.CamelFileName} -> output");
    }
    private void validateOrders(Exchange e) throws Exception {
        // Se valida una representación del texto; el cuerpo original no se reemplaza.
        String text = e.getMessage().getBody(String.class);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();
        try (CSVParser parser = format.parse(new StringReader(text))) {
            Set<String> expected = Set.of("pedido_id", "cliente", "producto", "cantidad", "precio_unitario");
            if (!parser.getHeaderMap().keySet().equals(expected) || parser.getHeaderNames().size() != 5)
                throw new IllegalArgumentException("Cabecera requerida: pedido_id,cliente,producto,cantidad,precio_unitario");
            int count = 0;
            for (CSVRecord row : parser) {
                count++;
                if (!row.isConsistent()) throw new IllegalArgumentException("Fila " + count + ": número de columnas incorrecto");
                for (String field : expected) if (row.get(field).isBlank())
                    throw new IllegalArgumentException("Fila " + count + ": campo vacío " + field);
                try {
                    if (Integer.parseInt(row.get("cantidad")) <= 0 || new BigDecimal(row.get("precio_unitario")).signum() <= 0)
                        throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Fila " + count + ": cantidad entera y precio deben ser positivos");
                }
            }
            if (count == 0) throw new IllegalArgumentException("CSV sin pedidos");
        } catch (java.io.UncheckedIOException | java.io.IOException ex) {
            throw new IllegalArgumentException("CSV no legible: " + ex.getMessage(), ex);
        }
    }
    private static String quote(Object value) {
        return "\"" + String.valueOf(value).replace("\"", "\"\"").replace('\n', ' ').replace('\r', ' ') + "\"";
    }
    private void audit(Exchange e, String status, String detail, Path file) throws Exception {
        if (!Files.exists(file)) Files.writeString(file, "fecha,archivo,estado,detalle\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        String line = quote(OffsetDateTime.now(ZoneOffset.ofHours(-5))) + ","
            + quote(e.getMessage().getHeader(Exchange.FILE_NAME)) + "," + quote(status) + "," + quote(detail) + "\n";
        Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }
}
