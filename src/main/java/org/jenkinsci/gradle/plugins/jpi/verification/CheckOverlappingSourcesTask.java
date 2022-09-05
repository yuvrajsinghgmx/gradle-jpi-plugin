package org.jenkinsci.gradle.plugins.jpi.verification;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.stream.Collectors.joining;

public abstract class CheckOverlappingSourcesTask extends DefaultTask {
    public static final String TASK_NAME = "checkOverlappingSources";

    @InputFiles
    public abstract Property<FileCollection> getClassesDirs();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void validate() {
        List<File> discovered = new LinkedList<>();
        Set<String> existingSezpozFiles = new HashSet<>();
        FileCollection classesDirs = getClassesDirs().get();
        for (File classDir : classesDirs) {
            File annotationsDir = new File(classDir, "META-INF/annotations");
            String[] files = annotationsDir.list();
            if (files == null) {
                continue;
            }
            for (String it : files) {
                File path = new File(annotationsDir, it);
                discovered.add(path);
                if (!path.isFile()) {
                    continue;
                }
                if (existingSezpozFiles.contains(it)) {
                    throw new GradleException("Found overlapping Sezpoz file: " + it + ". Use joint compilation!");
                }
                existingSezpozFiles.add(it);
            }
        }
        List<File> pluginImpls = new LinkedList<>();
        for (File classesDir : classesDirs) {
            File plugin = new File(classesDir, "META-INF/services/hudson.Plugin");
            if (plugin.exists()) {
                pluginImpls.add(plugin);
            }
        }

        if (pluginImpls.size() > 1) {
            String implementations = pluginImpls.stream().map(File::getPath).collect(joining(", "));
            throw new GradleException(String.format(
                    "Found multiple directories containing Jenkins plugin implementations ('%s'). " +
                            "Use joint compilation to work around this problem.",
                    implementations
            ));
        }
        discovered.addAll(pluginImpls);

        Path destination = getOutputFile().get().getAsFile().toPath();
        try (BufferedWriter w = Files.newBufferedWriter(destination, UTF_8, CREATE, TRUNCATE_EXISTING)) {
            for (File file : discovered) {
                w.append(file.getAbsolutePath()).append("\n");
            }
        } catch (IOException e) {
            throw new GradleException("Failed to write to " + destination, e);
        }
    }
}
