package Henok.example.DeutscheCollageBack_endAPI.Repository;

import Henok.example.DeutscheCollageBack_endAPI.Entity.StudentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentStatusRepo extends JpaRepository<StudentStatus, Long> {
    boolean existsByStatusName(String statusName);

    Optional<StudentStatus> findByStatusName(String active);

    boolean existsByStatusNameIgnoreCase(String statusName);
}