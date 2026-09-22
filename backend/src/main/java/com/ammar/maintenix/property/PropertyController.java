package com.ammar.maintenix.property;

import com.ammar.maintenix.property.dto.AddPropertyMemberRequest;
import com.ammar.maintenix.property.dto.CreatePropertyRequest;
import com.ammar.maintenix.property.dto.PropertyMemberResponse;
import com.ammar.maintenix.property.dto.PropertyResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    public ResponseEntity<PropertyResponse> createProperty(
            @Valid @RequestBody CreatePropertyRequest request
    ) {
        PropertyResponse response = propertyService.createProperty(request);
        return ResponseEntity.created(URI.create("/api/properties/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<PropertyResponse> getProperties(Authentication authentication) {
        return propertyService.getProperties(authentication.getName());
    }

    @GetMapping("/{propertyId}")
    public PropertyResponse getProperty(@PathVariable UUID propertyId) {
        return propertyService.getProperty(propertyId);
    }

    @PostMapping("/{propertyId}/members")
    public ResponseEntity<PropertyMemberResponse> addMember(
            @PathVariable UUID propertyId,
            @Valid @RequestBody AddPropertyMemberRequest request
    ) {
        PropertyMemberResponse response = propertyService.addMember(propertyId, request);
        return ResponseEntity.created(URI.create(
                        "/api/properties/" + propertyId + "/members/" + response.userId()))
                .body(response);
    }

    @GetMapping("/{propertyId}/members")
    public List<PropertyMemberResponse> getMembers(@PathVariable UUID propertyId) {
        return propertyService.getMembers(propertyId);
    }

    @DeleteMapping("/{propertyId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID propertyId,
            @PathVariable UUID userId
    ) {
        propertyService.removeMember(propertyId, userId);
        return ResponseEntity.noContent().build();
    }
}
