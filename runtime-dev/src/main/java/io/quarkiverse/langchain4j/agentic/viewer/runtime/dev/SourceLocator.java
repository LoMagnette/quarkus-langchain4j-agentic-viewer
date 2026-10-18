package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.CodeRef;
import io.quarkus.dev.console.DevConsoleManager;
import io.quarkus.dev.spi.HotReplacementContext;

/**
 * Finds the source file and line of a piece of application code (a {@link CodeRef}) in the source and resource
 * directories of the application in dev mode, for the Dev UI to open it in the IDE. Code of dependencies is not found.
 * Jandex has no line numbers and abstract methods have none in the bytecode: the line is the one declaring the method
 * (or the class) in the source.
 */
public final class SourceLocator {

    /**
     * Where a piece of code is.
     *
     * @param fileName {@code file://} URI of the file, as the Dev UI's open-in-IDE action takes it
     * @param lang file extension (java, kt, txt...)
     * @param lineNumber 1-based line
     */
    public record Location(String fileName, String lang, int lineNumber) {
    }

    private final List<Path> sources;
    private final List<Path> resources;

    public SourceLocator(List<Path> sources, List<Path> resources) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
        this.resources = resources == null ? List.of() : List.copyOf(resources);
    }

    /** The source and resource directories of the application in dev mode, none outside dev mode. */
    public static SourceLocator ofDevMode() {
        HotReplacementContext context = DevConsoleManager.getHotReplacementContext();
        return context == null ? new SourceLocator(List.of(), List.of())
                : new SourceLocator(context.getSourcesDir(), context.getResourcesDir());
    }

    public Optional<Location> locate(CodeRef code) {
        if (code == null || code.className() == null) {
            return Optional.empty();
        }
        try {
            return code.resource() != null ? resource(code) : source(code);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<Location> resource(CodeRef code) {
        String resource = code.resource().startsWith("/") ? code.resource().substring(1) : code.resource();
        String packageDir = packageName(code.className()).replace('.', '/');
        for (Path root : resources) {
            for (Path candidate : List.of(root.resolve(resource), root.resolve(packageDir).resolve(resource))) {
                if (Files.isRegularFile(candidate)) {
                    return Optional.of(new Location(candidate.toUri().toString(), extension(candidate), 1));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Location> source(CodeRef code) throws IOException {
        String topLevel = code.className().contains("$") ? code.className().substring(0, code.className().indexOf('$'))
                : code.className();
        String path = topLevel.replace('.', '/');
        for (Path root : sources) {
            for (String extension : List.of("java", "kt")) {
                Path file = root.resolve(path + "." + extension);
                if (Files.isRegularFile(file)) {
                    return Optional.of(new Location(file.toUri().toString(), extension,
                            code.line() != null ? code.line() : line(Files.readAllLines(file), code)));
                }
            }
        }
        return Optional.empty();
    }

    /** The line declaring the member, searched from the declaration of its (nested) class; the class line otherwise. */
    static int line(List<String> lines, CodeRef code) {
        String simpleName = code.className().substring(Math.max(code.className().lastIndexOf('.'),
                code.className().lastIndexOf('$')) + 1);
        Pattern type = Pattern.compile("\\b(class|interface|record|enum|object)\\s+" + Pattern.quote(simpleName) + "\\b");
        int start = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (!comment(lines.get(i)) && type.matcher(lines.get(i)).find()) {
                start = i;
                break;
            }
        }
        if (code.member() != null) {
            // a declaration, not a call (obj.member(...)) nor a comment
            Pattern member = Pattern.compile("(?<![\\w.$])" + Pattern.quote(code.member()) + "\\s*\\(");
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!comment(line) && !line.trim().startsWith("return ") && member.matcher(line).find()) {
                    return i + 1;
                }
            }
        }
        return start + 1;
    }

    private static boolean comment(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }

    private static String packageName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? "" : className.substring(0, dot);
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "txt" : name.substring(dot + 1);
    }
}
