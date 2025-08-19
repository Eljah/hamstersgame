package tools;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Example entry point that vectorizes every PNG found in the assets directory
 * into pen-style PNG and SVG images. Outputs are written to build/vectorized.
 */
public class VectorizeAssets {
    public static void main(String[] args) throws IOException {
        String color = args.length > 0 ? args[0] : "blue";
        Path assetsDir = Paths.get("assets");
        Path outDir = Paths.get("build", "vectorized");
        Files.createDirectories(outDir);
        try (Stream<Path> files = Files.walk(assetsDir)) {
            files.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                 .forEach(p -> {
                     try {
                         Path target = outDir.resolve(p.getFileName());
                         Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                         PenVectorize.main(new String[]{target.toString(), "--color", color});
                         System.out.println("Vectorized " + p + " -> " + target.resolveSibling(
                                 target.getFileName().toString().replaceFirst("\\.png$", ".svg")));
                     } catch (IOException e) {
                         throw new RuntimeException(e);
                     }
                 });
        }
    }
}
