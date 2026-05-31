package com.teamhub.schemas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Validates all preset JSON files from {@code classpath:schema-presets/*.json} at application
 * startup (TASK-040 test step 2).
 *
 * <p>Each file must be a valid JSON array of {@link FieldDefinition} objects. If any file cannot be
 * deserialized, the application fails to start with a descriptive error. This guarantees that the
 * JSON files in source control are always structurally correct before deployment.
 */
@Component
public class SchemaPresetLoader implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SchemaPresetLoader.class);

  private static final TypeReference<List<FieldDefinition>> FIELD_DEF_LIST =
      new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public SchemaPresetLoader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources("classpath:schema-presets/*.json");

    if (resources.length == 0) {
      throw new IllegalStateException(
          "No schema preset JSON files found under classpath:schema-presets/. "
              + "Expected eight presets (PRD §8.4).");
    }

    for (Resource resource : resources) {
      String filename = resource.getFilename();
      try (InputStream in = resource.getInputStream()) {
        List<FieldDefinition> fields = objectMapper.readValue(in, FIELD_DEF_LIST);
        if (fields == null || fields.isEmpty()) {
          throw new IllegalStateException(
              "Schema preset file '"
                  + filename
                  + "' is empty. Each preset must define at least one field.");
        }
        for (FieldDefinition fd : fields) {
          if (fd.key() == null || fd.key().isBlank()) {
            throw new IllegalStateException(
                "Schema preset file '"
                    + filename
                    + "' contains a FieldDefinition with a null or blank key.");
          }
          if (fd.label() == null || fd.label().isBlank()) {
            throw new IllegalStateException(
                "Schema preset file '"
                    + filename
                    + "' contains field '"
                    + fd.key()
                    + "' with a null or blank label.");
          }
          if (fd.type() == null || fd.type().isBlank()) {
            throw new IllegalStateException(
                "Schema preset file '"
                    + filename
                    + "' contains field '"
                    + fd.key()
                    + "' with a null or blank type.");
          }
        }
        log.info("Schema preset '{}' validated: {} field(s)", filename, fields.size());
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to validate schema preset file '" + filename + "': " + e.getMessage(), e);
      }
    }

    log.info("All {} schema preset file(s) validated successfully.", resources.length);
  }
}
