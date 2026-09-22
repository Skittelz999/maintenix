package com.ammar.maintenix.property;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyMemberRepository extends JpaRepository<PropertyMember, UUID> {

    boolean existsByPropertyIdAndUserId(UUID propertyId, UUID userId);

    @EntityGraph(attributePaths = {"property", "user"})
    List<PropertyMember> findAllByPropertyId(UUID propertyId);

    @EntityGraph(attributePaths = {"property"})
    List<PropertyMember> findAllByUserId(UUID userId);

    Optional<PropertyMember> findByPropertyIdAndUserId(UUID propertyId, UUID userId);
}
