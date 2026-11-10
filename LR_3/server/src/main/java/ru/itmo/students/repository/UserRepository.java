package ru.itmo.students.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itmo.students.entity.UserEntity;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
}
