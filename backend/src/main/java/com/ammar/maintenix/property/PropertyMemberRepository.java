package com.ammar.maintenix.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PropertyMemberRepository extends JpaRepository<PropertyMember, UUID> {

    boolean existsByPropertyIdAndUserId(UUID propertyId, UUID userId);
}
