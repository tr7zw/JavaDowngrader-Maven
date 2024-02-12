package dev.tr7zw.maven.javadowngrader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.additionalclassprovider.LazyFileClassProvider;
import net.lenni0451.classtransform.additionalclassprovider.PathClassProvider;
import net.lenni0451.classtransform.utils.tree.BasicClassProvider;
import net.raphimc.javadowngrader.impl.classtransform.JavaDowngraderTransformer;
import net.raphimc.javadowngrader.impl.classtransform.util.ClassNameUtil;
import net.raphimc.javadowngrader.runtime.RuntimeRoot;

@Mojo(name = "javadowngrade", defaultPhase = LifecyclePhase.PACKAGE)
public class JavaDowngraderMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.jar", readonly = true)
    private File inputFile;

    @Parameter(defaultValue = "-pre-downgrade")
    private String outputSuffix;

    @Parameter(defaultValue = "52")
    private Integer targetVersion;

    @Parameter(defaultValue = "true")
    private Boolean copyRuntimeClasses;

    public void execute() throws MojoExecutionException, MojoFailureException {
        System.out.println("Downgrading jar: " + inputFile);

        final String outputName = inputFile.getName().substring(0, inputFile.getName().length() - 4) + "-downgrading";
        final File outputFile = new File(inputFile.getParentFile(), outputName + ".jar");

        try (FileSystem inFs = FileSystems.newFileSystem(inputFile.toPath(), (ClassLoader) null)) {
            final Path inRoot = inFs.getRootDirectories().iterator().next();

            final Collection<String> runtimeDeps = new HashSet<>();
            final TransformerManager transformerManager = new TransformerManager(new PathClassProvider(inRoot,
                    new LazyFileClassProvider(getCompileClassPath(), new BasicClassProvider())));
            transformerManager.addBytecodeTransformer(
                    JavaDowngraderTransformer.builder(transformerManager).targetVersion(targetVersion)
                            .classFilter(c -> Files.isRegularFile(inRoot.resolve(ClassNameUtil.toClassFilename(c))))
                            .depCollector(runtimeDeps::add).build());

            try (FileSystem outFs = FileSystems.newFileSystem(new URI("jar:" + outputFile.toURI()),
                    Collections.singletonMap("create", "true"))) {
                final Path outRoot = outFs.getRootDirectories().iterator().next();

                // Downgrade classes
                try (Stream<Path> stream = Files.walk(inRoot)) {
                    stream.forEach(path -> {
                        try {
                            final String relative = ClassNameUtil.slashName(inRoot.relativize(path));
                            final Path dest = outRoot.resolve(relative);
                            if (Files.isDirectory(path)) {
                                Files.createDirectories(dest);
                                return;
                            }
                            final Path parent = dest.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                            if (!relative.endsWith(".class") || relative.contains("META-INF/versions/")) {
                                Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                                return;
                            }
                            final String className = ClassNameUtil.toClassName(relative);
                            final byte[] bytecode = Files.readAllBytes(path);
                            final byte[] result;
                            try {
                                result = transformerManager.transform(className, bytecode);
                            } catch (Throwable e) {
                                throw new RuntimeException("Failed to transform " + className, e);
                            }
                            Files.write(dest, result != null ? result : bytecode);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                }

                // Copy runtime classes
                if (copyRuntimeClasses) {
                    for (final String runtimeDep : runtimeDeps) {
                        final String classPath = runtimeDep.concat(".class");
                        try (InputStream is = RuntimeRoot.class.getResourceAsStream("/" + classPath)) {
                            if (is == null) {
                                throw new IllegalStateException("Missing runtime class " + runtimeDep);
                            }
                            final Path dest = outRoot.resolve(classPath);
                            final Path parent = dest.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new MojoExecutionException("Error during copy runtime classes.", e);
                        }
                    }
                }
            } catch (IOException | URISyntaxException e1) {
                throw new MojoExecutionException("Error during remapping.", e1);
            }
        } catch (IOException e2) {
            throw new MojoExecutionException("Error during remapping.", e2);
        }
        final String originalOutputName = inputFile.getName().substring(0, inputFile.getName().length() - 4)
                + outputSuffix;
        final File originalOutputFile = new File(inputFile.getParentFile(), originalOutputName + ".jar");
        try {
            Files.move(inputFile.toPath(), originalOutputFile.toPath());
            Files.move(outputFile.toPath(), inputFile.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Error while replacing the output file.", e);
        }
    }

    private List<File> getCompileClassPath() throws MojoExecutionException {
        try {
            List<String> elements = (List<String>) getPluginContext().get("project.compileClasspathElements");
            if (elements == null) {
                return Collections.emptyList();
            }
            List<File> classpathFiles = new ArrayList<>();
            for (String element : elements) {
                classpathFiles.add(new File(element));
            }
            return classpathFiles;
        } catch (Exception e) {
            throw new MojoExecutionException("Error retrieving compile classpath elements", e);
        }
    }

}
