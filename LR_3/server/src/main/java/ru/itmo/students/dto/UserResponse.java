package ru.itmo.students.dto;

import ru.itmo.students.entity.Role;
import ru.itmo.students.entity.UserEntity;

public record UserResponse(Long id, String username, String email, Role role) {
}
