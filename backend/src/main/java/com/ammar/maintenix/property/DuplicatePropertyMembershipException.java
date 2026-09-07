package com.ammar.maintenix.property;

import java.util.UUID;

public class DuplicatePropertyMembershipException extends RuntimeException {

    public DuplicatePropertyMembershipException(UUID propertyId, UUID userId) {
        super("User " + userId + " is already a member of property " + propertyId);
    }
}
