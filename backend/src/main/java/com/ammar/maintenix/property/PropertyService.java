package com.ammar.maintenix.property;

import com.ammar.maintenix.property.dto.AddPropertyMemberRequest;
import com.ammar.maintenix.property.dto.CreatePropertyRequest;
import com.ammar.maintenix.property.dto.PropertyMemberResponse;
import com.ammar.maintenix.property.dto.PropertyResponse;
import com.ammar.maintenix.user.User;
import com.ammar.maintenix.user.UserRepository;
import com.ammar.maintenix.user.UserRole;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyMemberRepository propertyMemberRepository;
    private final UserRepository userRepository;

    public PropertyService(
            PropertyRepository propertyRepository,
            PropertyMemberRepository propertyMemberRepository,
            UserRepository userRepository
    ) {
        this.propertyRepository = propertyRepository;
        this.propertyMemberRepository = propertyMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PropertyResponse createProperty(CreatePropertyRequest request) {
        Property property = new Property(
                request.getName().strip(),
                request.getAddressLine().strip(),
                request.getPostalCode().strip(),
                request.getCity().strip()
        );
        return mapProperty(propertyRepository.save(property));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<PropertyResponse> getProperties() {
        return propertyRepository.findAll().stream()
                .map(this::mapProperty)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PropertyResponse getProperty(UUID propertyId) {
        return mapProperty(findProperty(propertyId));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PropertyMemberResponse addMember(
            UUID propertyId,
            AddPropertyMemberRequest request
    ) {
        Property property = findProperty(propertyId);
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found with id: " + request.getUserId()));

        if (!user.isActive()) {
            throw new IllegalArgumentException("User account is inactive");
        }
        if (user.getRole() != UserRole.TENANT) {
            throw new IllegalArgumentException("Only tenants can be property members");
        }
        if (propertyMemberRepository.existsByPropertyIdAndUserId(propertyId, user.getId())) {
            throw new DuplicatePropertyMembershipException(propertyId, user.getId());
        }

        try {
            return mapMember(propertyMemberRepository.saveAndFlush(
                    new PropertyMember(property, user)));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicatePropertyMembershipException(propertyId, user.getId());
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<PropertyMemberResponse> getMembers(UUID propertyId) {
        findProperty(propertyId);
        return propertyMemberRepository.findAllByPropertyId(propertyId).stream()
                .map(this::mapMember)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void removeMember(UUID propertyId, UUID userId) {
        findProperty(propertyId);
        PropertyMember membership = propertyMemberRepository
                .findByPropertyIdAndUserId(propertyId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Property membership not found for user: " + userId));
        propertyMemberRepository.delete(membership);
    }

    private Property findProperty(UUID propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Property not found with id: " + propertyId));
    }

    private PropertyResponse mapProperty(Property property) {
        return new PropertyResponse(
                property.getId(),
                property.getName(),
                property.getAddressLine(),
                property.getPostalCode(),
                property.getCity(),
                property.isActive(),
                property.getCreatedAt(),
                property.getUpdatedAt()
        );
    }

    private PropertyMemberResponse mapMember(PropertyMember membership) {
        User user = membership.getUser();
        return new PropertyMemberResponse(
                membership.getId(),
                membership.getProperty().getId(),
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                membership.getCreatedAt()
        );
    }
}
