# Java Downgrader Maven Plugin

Maven plugin implementation of the [Java Downgrader](https://github.com/RaphiMC/JavaDowngrader) Gradle plugin. The original file will be stored as with a ``-pre-downgrade`` suffix and the downgraded file will be stored under the original name.

## Properties

- ``inputFile`` - The input file to downgrade. Defaults to ``${project.build.directory}/${project.build.finalName}.jar``
- ``outputSuffix`` - The suffix to append to the original file. Defaults to ``-pre-downgrade``
- ``targetVersion`` - The target class version to downgrade to. Defaults to ``52``(Java 8)
- ``copyRuntimeClasses`` - If the runtime classes should be copied to the output file. Defaults to ``true`` (currently not working)

## Usage

```xml
      <plugin>
        <groupId>dev.tr7zw.javadowngrader.maven</groupId>
        <artifactId>javadowngrader-maven-plugin</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <executions>
          <execution>
            <id>javadowngrade</id>
            <goals>
              <goal>javadowngrade</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
```

When combining with shading make sure to place the plugin after the shading plugin in the build process, so the shaded jar is downgraded.
