package com.ammar.maintenix.user;

import com.ammar.maintenix.user.dto.CreateUserRequest;
import com.ammar.maintenix.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.created(
                URI.create("/api/users/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<UserResponse> getUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active
    ) {
        return userService.getUsers(role, active);
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable UUID userId) {
        return userService.getUser(userId);
    }
}
