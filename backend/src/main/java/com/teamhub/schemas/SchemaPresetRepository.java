package com.teamhub.schemas;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link SchemaPreset} (PRD §8.4). */
public interface SchemaPresetRepository extends JpaRepository<SchemaPreset, String> {}
