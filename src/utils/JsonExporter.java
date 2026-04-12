package utils;

import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Serializes the full simulator state to state.json using manual JSON construction.
 */
public class JsonExporter {
    private String outputPath;

    /** Sets the output path for exported JSON state. */
    public JsonExporter(String outputPath) {
        this.outputPath = outputPath;
    }

    /** Builds the root JSON object and writes current path, tree, and heap state to disk. */
    public void export(models.FileNode root, models.FileNode currentDirectory, datastructures.FileHeap heap) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        String currentPath = "/";
        if (currentDirectory != null && currentDirectory.absolutePath != null) {
            currentPath = currentDirectory.absolutePath.replace("\"", "\\\"");
        }

        sb.append("  \"currentPath\": \"").append(currentPath).append("\",\n");
        sb.append("  \"tree\": ");
        nodeToJson(root, sb);
        sb.append(",\n");
        sb.append("  \"heap\": ");
        heapToJson(heap, sb);
        sb.append("\n");
        sb.append("}");

        writeToFile(sb.toString());
    }

    /** Serializes a directory node recursively with its files and children arrays. */
    private void nodeToJson(models.FileNode node, StringBuilder sb) {
        sb.append("{");

        String nodeName = node != null && node.name != null ? node.name.replace("\"", "\\\"") : "";
        String nodePath = node != null && node.absolutePath != null ? node.absolutePath.replace("\"", "\\\"") : "";

        sb.append("\"name\":\"").append(nodeName).append("\",");
        sb.append("\"path\":\"").append(nodePath).append("\",");
        sb.append("\"isDirectory\":true,");

        sb.append("\"files\":[");
        java.util.ArrayList<models.FileMetadata> files = new java.util.ArrayList<>();
        if (node != null && node.files != null) {
            files = node.files.getAll();
        }
        for (int i = 0; i < files.size(); i++) {
            metadataToJson(files.get(i), sb);
            if (i < files.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("],");

        sb.append("\"children\":[");
        java.util.ArrayList<models.FileNode> children = new java.util.ArrayList<>();
        if (node != null && node.children != null) {
            children = node.children;
        }
        for (int i = 0; i < children.size(); i++) {
            nodeToJson(children.get(i), sb);
            if (i < children.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");

        sb.append("}");
    }

    /** Serializes one file metadata object with formatted timestamps and escaped strings. */
    private void metadataToJson(models.FileMetadata m, StringBuilder sb) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String filename = m.filename != null ? m.filename.replace("\"", "\\\"") : "";
        String formattedSize = m.formattedSize() != null ? m.formattedSize().replace("\"", "\\\"") : "";
        String type = m.type != null ? m.type.replace("\"", "\\\"") : "";
        String createdAt = m.createdAt != null ? m.createdAt.format(formatter).replace("\"", "\\\"") : "";
        String modifiedAt = m.modifiedAt != null ? m.modifiedAt.format(formatter).replace("\"", "\\\"") : "";

        sb.append("{");
        sb.append("\"filename\":\"").append(filename).append("\",");
        sb.append("\"sizeBytes\":").append(m.sizeBytes).append(",");
        sb.append("\"formattedSize\":\"").append(formattedSize).append("\",");
        sb.append("\"type\":\"").append(type).append("\",");
        sb.append("\"createdAt\":\"").append(createdAt).append("\",");
        sb.append("\"modifiedAt\":\"").append(modifiedAt).append("\"");
        sb.append("}");
    }

    /** Serializes the heap as a largest-to-smallest array using topK(heap.size()). */
    private void heapToJson(datastructures.FileHeap heap, StringBuilder sb) {
        sb.append("[");

        java.util.ArrayList<datastructures.FileHeap.HeapEntry> entries = heap.topK(heap.size());
        for (int i = 0; i < entries.size(); i++) {
            datastructures.FileHeap.HeapEntry e = entries.get(i);
            String filename = e.filename != null ? e.filename.replace("\"", "\\\"") : "";
            String absolutePath = e.absolutePath != null ? e.absolutePath.replace("\"", "\\\"") : "";

            sb.append("{");
            sb.append("\"filename\":\"").append(filename).append("\",");
            sb.append("\"absolutePath\":\"").append(absolutePath).append("\",");
            sb.append("\"sizeBytes\":").append(e.sizeBytes);
            sb.append("}");

            if (i < entries.size() - 1) {
                sb.append(",");
            }
        }

        sb.append("]");
    }

    /** Writes the generated JSON string to the configured output file path. */
    private void writeToFile(String json) {
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(json);
        } catch (IOException e) {
            Logger.error("Error writing JSON to file: " + e.getMessage());
        }
    }
}
