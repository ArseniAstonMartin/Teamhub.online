package com.teamhub.security.rbac;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller used to exercise permission-based {@code @PreAuthorize} guards end-to-end
 * through the real security filter chain (TASK-023 test steps 2 and 3).
 *
 * <p>This class lives under {@code src/test} and is registered explicitly via {@code @Import} in
 * the relevant tests, so it is never part of the production component scan or the shipped
 * application. It demonstrates the intended controller pattern: guards reference a permission code
 * via {@code hasAuthority('PERM_<code>')}, never a hard-coded role.
 */
@RestController
@RequestMapping("/test/rbac")
public class GuardedTestController {

  @GetMapping("/jobs")
  @PreAuthorize("hasAuthority('PERM_job.read.tenant')")
  public String readTenantJobs() {
    return "tenant-jobs";
  }

  @GetMapping("/jobs/hard-delete")
  @PreAuthorize("hasAuthority('PERM_job.delete.hard')")
  public String hardDeleteJob() {
    return "hard-deleted";
  }

  @GetMapping("/schema")
  @PreAuthorize("hasAuthority('PERM_schema.edit')")
  public String editSchema() {
    return "schema-edited";
  }
}
