package com.teamhub.customer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Customer} entities. */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {}
