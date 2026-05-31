package com.teamhub.schemas;

import com.teamhub.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when a requested appointment-schema version does not exist (PRD §8.2). */
public class SchemaNotFoundException extends DomainException {

  public static final String CODE = "SCHEMA_NOT_FOUND";

  public SchemaNotFoundException(String name, int version) {
    super(CODE, HttpStatus.NOT_FOUND, "Schema '" + name + "' version " + version + " not found");
  }

  public SchemaNotFoundException(java.util.UUID id) {
    super(CODE, HttpStatus.NOT_FOUND, "Schema with id '" + id + "' not found");
  }
}
