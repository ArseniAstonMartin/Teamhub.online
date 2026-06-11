package com.teamhub.internal.dto;

import com.teamhub.schemas.SchemaPreset;

/** A schema preset option for the internal tenant-create form (TASK-088 prerequisite). */
public record SchemaPresetItemResponse(String presetKey, String schemaName) {

  public static SchemaPresetItemResponse from(SchemaPreset preset) {
    return new SchemaPresetItemResponse(preset.getPresetKey(), preset.getSchemaName());
  }
}
