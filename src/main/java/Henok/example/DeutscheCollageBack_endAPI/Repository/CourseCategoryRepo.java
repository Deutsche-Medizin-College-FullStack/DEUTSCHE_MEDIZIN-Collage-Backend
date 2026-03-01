package Henok.example.DeutscheCollageBack_endAPI.Repository;

import Henok.example.DeutscheCollageBack_endAPI.Entity.CourseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseCategoryRepo extends JpaRepository<CourseCategory, Long> {
    boolean existsByCatName(String catName);

    boolean existsByCatNameIgnoreCase(String catName);
}
