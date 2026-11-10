package ru.itmo.students.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import ru.itmo.students.entity.StudentEntity;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<StudentEntity, Long>,
        JpaSpecificationExecutor<StudentEntity> {
    Optional<StudentEntity> findByIsu(Integer isu);
    boolean existsByIsu(Integer isu);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from StudentEntity student where student.isu = :isu")
    Optional<StudentEntity> findByIsuForUpdate(@Param("isu") Integer isu);
}
