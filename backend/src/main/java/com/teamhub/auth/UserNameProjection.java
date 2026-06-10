package com.teamhub.auth;

import java.util.UUID;

/** Id + name + email for assignee display-name resolution (TASK-130). */
public interface UserNameProjection {

  UUID getId();

  String getName();

  String getEmail();
}
