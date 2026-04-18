package io.wfs.ui.util;

import java.util.Set;

/**
 * Detects file types based on extension for choosing the right viewer
 * and providing UI hints. Uses the Strategy pattern concept — callers
 * choose rendering based on the returned category.
 */
public final class FileTypeDetector {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "csv", "log", "xml", "html", "htm", "css", "js",
            "json", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties",
            "java", "py", "rb", "c", "cpp", "h", "hpp", "rs", "go", "kt",
            "scala", "groovy", "sh", "bash", "zsh", "bat", "cmd", "ps1",
            "sql", "gradle", "sbt", "makefile", "dockerfile");

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "svg", "webp");

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "class", "jar", "war", "ear", "exe", "dll", "so", "o",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "tar", "gz", "bz2", "xz", "7z", "rar");

    public enum FileType {
        TEXT, IMAGE, BINARY, UNKNOWN
    }

    private FileTypeDetector() {
    }

    public static FileType detect(String extension) {
        if (extension == null || extension.isEmpty()) {
            return FileType.TEXT; // Default to text for extensionless files
        }
        String ext = extension.toLowerCase();
        if (TEXT_EXTENSIONS.contains(ext))
            return FileType.TEXT;
        if (IMAGE_EXTENSIONS.contains(ext))
            return FileType.IMAGE;
        if (BINARY_EXTENSIONS.contains(ext))
            return FileType.BINARY;
        return FileType.UNKNOWN;
    }

    public static String getMimeDescription(String extension) {
        FileType type = detect(extension);
        return switch (type) {
            case TEXT -> "Text File";
            case IMAGE -> "Image File";
            case BINARY -> "Binary File";
            case UNKNOWN -> "Unknown File";
        };
    }
}
