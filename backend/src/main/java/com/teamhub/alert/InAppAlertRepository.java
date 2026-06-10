package com.teamhub.alert;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link InAppAlert} (PRD §28, TASK-118). */
public interface InAppAlertRepository extends JpaRepository<InAppAlert, UUID> {

  List<InAppAlert> findByUserIdAndDismissedFalse(UUID userId);
}
