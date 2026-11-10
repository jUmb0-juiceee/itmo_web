package ru.itmo.students.dto;

import ru.itmo.students.entity.Role;
import ru.itmo.students.entity.UserEntity;

public record UserResponse(Long id, String username, String email, Role role) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
