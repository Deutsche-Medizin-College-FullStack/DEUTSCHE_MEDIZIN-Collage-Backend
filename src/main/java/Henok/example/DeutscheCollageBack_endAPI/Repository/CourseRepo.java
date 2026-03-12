package Henok.example.DeutscheCollageBack_endAPI.Repository;

import Henok.example.DeutscheCollageBack_endAPI.Entity.Course;
import Henok.example.DeutscheCollageBack_endAPI.Entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepo extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {
    boolean existsBycCode(String cCode);
    // Find course by its unique code - used for prerequisite resolution
    Optional<Course> findBycCode(String code);


    List<Course> findByDepartment(Department department);

    // Custom query to find distinct courses by a list of departments
    @Query("SELECT DISTINCT c FROM Course c WHERE c.department IN :departments")
    List<Course> findDistinctByDepartmentIn(@Param("departments") List<Department> departments);

    // Check if a course with the same code AND same department already exists
    // department can be null → handled with COALESCE in query
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Course c " +
            "WHERE c.cCode = :code " +
            "AND (c.department.id = :departmentId " +
            "     OR (c.department IS NULL AND :departmentId IS NULL))")
    boolean existsByCCodeAndDepartmentId(@Param("code") String code,
                                         @Param("departmentId") Long departmentId);


    /**
     * Finds all courses that belong to the given department
     * and are NOT in the EXTERNAL category (catID = 5).
     * These represent the actual campus curriculum courses a student is expected to take.
     */
    @Query("SELECT c FROM Course c " +
            "WHERE c.department = :department " +
            "AND c.category.catID != :excludedCategoryId")
    List<Course> findCurriculumCoursesByDepartment(
            @Param("department") Department department,
            @Param("excludedCategoryId") Long excludedCategoryId
    );
}