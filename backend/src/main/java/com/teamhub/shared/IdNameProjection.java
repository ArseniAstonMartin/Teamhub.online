package com.teamhub.shared;

import java.util.UUID;

/** Lightweight id + display-name pair for batch enrichment queries (TASK-130). */
public interface IdNameProjection {

  UUID getId();

  String getName();
}
