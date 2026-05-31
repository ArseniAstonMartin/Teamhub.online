package com.teamhub.schemas;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the error paths inside {@link SchemaPresetLoader#run}. The real classpath preset
 * JSON files are located by the production {@link
 * org.springframework.core.io.support.PathMatchingResourcePatternResolver}, but the injected {@link
 * ObjectMapper} is mocked to return invalid data so each guard is exercised without spinning up a
 * Spring context or a database.
 */
@ExtendWith(MockitoExtension.class)
class SchemaPresetLoaderTest {

  @Mock private ObjectMapper objectMapper;

  // ─── empty field list ─────────────────────────────────────────────────────

  /**
   * When the ObjectMapper deserialises a preset file as an empty list, {@link
   * SchemaPresetLoader#run} must throw {@link IllegalStateException} with a message that mentions
   * the file and the word "empty". Covers line 53 and the catch/rethrow block (lines 83-85).
   */
  @Test
  @SuppressWarnings("unchecked")
  void run_emptyFieldList_throwsIllegalStateException() throws Exception {
    when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
        .thenReturn(List.of());

    SchemaPresetLoader loader = new SchemaPresetLoader(objectMapper);

    assertThatThrownBy(() -> loader.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty");
  }

  // ─── blank key ────────────────────────────────────────────────────────────

  /**
   * A preset file whose first field has a blank {@code key} must cause {@link
   * SchemaPresetLoader#run} to throw {@link IllegalStateException} mentioning a "null or blank
   * key". Covers lines 60-63.
   */
  @Test
  @SuppressWarnings("unchecked")
  void run_blankKey_throwsIllegalStateException() throws Exception {
    FieldDefinition badFd =
        new FieldDefinition("", "Some Label", "text", false, 1, null, null, null, null);
    when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
        .thenReturn(List.of(badFd));

    SchemaPresetLoader loader = new SchemaPresetLoader(objectMapper);

    assertThatThrownBy(() -> loader.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("null or blank key");
  }

  // ─── blank label ──────────────────────────────────────────────────────────

  /**
   * A preset file whose first field has a blank {@code label} must cause {@link
   * SchemaPresetLoader#run} to throw {@link IllegalStateException} mentioning a "null or blank
   * label". Covers lines 66-71.
   */
  @Test
  @SuppressWarnings("unchecked")
  void run_blankLabel_throwsIllegalStateException() throws Exception {
    FieldDefinition badFd =
        new FieldDefinition("valid_key", "", "text", false, 1, null, null, null, null);
    when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
        .thenReturn(List.of(badFd));

    SchemaPresetLoader loader = new SchemaPresetLoader(objectMapper);

    assertThatThrownBy(() -> loader.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("null or blank label");
  }

  // ─── blank type ───────────────────────────────────────────────────────────

  /**
   * A preset file whose first field has a blank {@code type} must cause {@link
   * SchemaPresetLoader#run} to throw {@link IllegalStateException} mentioning a "null or blank
   * type". Covers lines 74-80.
   */
  @Test
  @SuppressWarnings("unchecked")
  void run_blankType_throwsIllegalStateException() throws Exception {
    FieldDefinition badFd =
        new FieldDefinition("valid_key", "Valid Label", "", false, 1, null, null, null, null);
    when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
        .thenReturn(List.of(badFd));

    SchemaPresetLoader loader = new SchemaPresetLoader(objectMapper);

    assertThatThrownBy(() -> loader.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("null or blank type");
  }

  // ─── null variants (short-circuit branches) ───────────────────────────────

  /**
   * When the ObjectMapper returns {@code null} (instead of an empty list), the {@code fields ==
   * null} short-circuit branch of the guard on line 52 must be triggered. Covers the {@code null}
   * path of the {@code if (fields == null || fields.isEmpty())} condition.
   */
  @Test
  @SuppressWarnings("unchecked")
  void run_nullFieldList_throwsIllegalStateException() throws Exception {
    when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class))).thenReturn(null);

    SchemaPresetLoader loader = new SchemaPresetLoader(objectMapper);

    assertThatThrownBy(() -> loader.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty");
  }

  /**
   * A preset file whose first field has a {@code null} key (as opposed to a blank string) must
   * trigger the {@code fd.key() == null} short-circuit branch on line 59. Covers the {@code null}
   * path of the {@code if (fd.key() == null || fd.key().isBlank())} condition.
   */
  @Test
  @SuppressWarnings("unchecked")
  void run_nullKey_throwsIllegalStateException() throws Exception {
    FieldDefinition badFd =
        new FieldDefinition(null, "Some Label", "text", false, 1, null, null, null, null);
    when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
        .thenReturn(List.of(badFd));

    SchemaPresetLoader loader = new SchemaPresetLoader(objectMapper);

    assertThatThrownBy(() -> loader.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("null or blank key");
  }

  /**
   * A preset file whose first field has a {@code null} label must trigger the {@code fd.label() ==
   * null} short-circuit branch on line 65. Covers the {@code null} path of the {@code if
   * (fd.label() == null || fd.label().isBlank())} condition.
   */
  @Test
  @SuppressWarnings("unchecked")
  void run_nullLabel_throwsIllegalStateException() throws Exception {
    FieldDefinition badFd =
        new FieldDefinition("valid_key", null, "text", false, 1, null, null, null, null);
    when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
        .thenReturn(List.of(badFd));

    SchemaPresetLoader loader = new SchemaPresetLoader(objectMapper);

    assertThatThrownBy(() -> loader.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("null or blank label");
  }

  /**
   * A preset file whose first field has a {@code null} type must trigger the {@code fd.type() ==
   * null} short-circuit branch on line 73. Covers the {@code null} path of the {@code if (fd.type()
   * == null || fd.type().isBlank())} condition.
   */
  @Test
  @SuppressWarnings("unchecked")
  void run_nullType_throwsIllegalStateException() throws Exception {
    FieldDefinition badFd =
        new FieldDefinition("valid_key", "Valid Label", null, false, 1, null, null, null, null);
    when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
        .thenReturn(List.of(badFd));

    SchemaPresetLoader loader = new SchemaPresetLoader(objectMapper);

    assertThatThrownBy(() -> loader.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("null or blank type");
  }
}
