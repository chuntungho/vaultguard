package com.vaultguard.db.repository;

import com.vaultguard.db.entity.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, String> {

    Page<Organization> findByNameContainingIgnoreCaseOrBillingEmailContainingIgnoreCase(
        String name, String email, Pageable pageable);
}
