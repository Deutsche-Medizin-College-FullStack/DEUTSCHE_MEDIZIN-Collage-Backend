package Henok.example.DeutscheCollageBack_endAPI.Repository;

import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.ProgramModality;
import Henok.example.DeutscheCollageBack_endAPI.Enums.DocumentStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Gender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface StudentDetailsRepository extends JpaRepository<StudentDetails, Long> {
    Optional<StudentDetails> findByPhoneNumber(String phoneNumber);

    Optional<StudentDetails> findByUser(User student);

    boolean existsByUserId(Long userId);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByExitExamUserID(String exitExamUserID);
    boolean existsByProgramModality(ProgramModality modality);


    // Fetches all students by a list of IDs with necessary relations eagerly loaded
    @Query("""
        SELECT DISTINCT s FROM StudentDetails s
        JOIN FETCH s.user u
        JOIN FETCH s.departmentEnrolled d
        JOIN FETCH s.programModality pm
        JOIN FETCH s.batchClassYearSemester bcys
        JOIN FETCH bcys.classYear cy
        JOIN FETCH bcys.semester sem
        WHERE s.id IN :ids
        """)
    List<StudentDetails> findAllByIdInWithRelations(@Param("ids") List<Long> ids);

    // Finds distinct departments for a list of student IDs
    // Why: Efficiently retrieves unique departments for slip generation
    @Query("""
    SELECT DISTINCT s.departmentEnrolled 
    FROM StudentDetails s 
    WHERE s.id IN :studentIds 
    AND s.departmentEnrolled IS NOT NULL
    """)
    List<Department> findDistinctDepartmentsByStudentIds(@Param("studentIds") List<Long> studentIds);

    // Example: find students in same batch and department as the course
    @Query("SELECT s FROM StudentDetails s " +
            "WHERE s.batchClassYearSemester = :bcys " +
            "AND s.departmentEnrolled = :department")
    List<StudentDetails> findByBatchClassYearSemesterAndDepartmentEnrolled(
            @Param("bcys") BatchClassYearSemester bcys,
            @Param("department") Department department);


    List<StudentDetails> findByDepartmentEnrolled(Department department);

    //===========================================================
    // Count students grouped by program level name.
// Returns Map<levelName, count>.
    @Query("SELECT pl.name AS levelName, COUNT(s) AS count " +
            "FROM StudentDetails s " +
            "JOIN s.programModality pm " +
            "JOIN pm.programLevel pl " +
            "GROUP BY pl.name")
    List<Object[]> countStudentsByProgramLevelRaw();
    default Map<String, Long> countStudentsByProgramLevel() {
        return countStudentsByProgramLevelRaw().stream()
                .collect(Collectors.toMap(arr -> (String) arr[0], arr -> (Long) arr[1]));
    }

    // Count students grouped by modality.
    // Similar mapping.
    @Query("SELECT pm.modality AS modality, COUNT(s) AS count " +
            "FROM StudentDetails s " +
            "JOIN s.programModality pm " +
            "GROUP BY pm.modality")
    List<Object[]> countStudentsByModalityRaw();
    default Map<String, Long> countStudentsByModality() {
        return countStudentsByModalityRaw().stream()
                .collect(Collectors.toMap(arr -> (String) arr[0], arr -> (Long) arr[1]));
    }

    // Students per department.
    @Query("SELECT d.deptName AS deptName, COUNT(s) AS count " +
            "FROM StudentDetails s " +
            "JOIN s.departmentEnrolled d " +
            "GROUP BY d.deptName")
    List<Object[]> countStudentsPerDepartmentRaw();
    default Map<String, Long> countStudentsPerDepartment() {
        return countStudentsPerDepartmentRaw().stream()
                .collect(Collectors.toMap(arr -> (String) arr[0], arr -> (Long) arr[1]));
    }

    // Enrollment trend by academic year code.
    @Query("SELECT ay.yearCode AS yearCode, COUNT(s) AS count " +
            "FROM StudentDetails s " +
            "JOIN s.academicYear ay " +
            "GROUP BY ay.yearCode " +
            "ORDER BY ay.yearCode")
    List<Object[]> getEnrollmentTrendByAcademicYearRaw();
    default Map<String, Long> getEnrollmentTrendByAcademicYear() {
        return getEnrollmentTrendByAcademicYearRaw().stream()
                .collect(Collectors.toMap(arr -> (String) arr[0], arr -> (Long) arr[1]));
    }

    // Gender distribution.
    @Query("SELECT s.gender AS gender, COUNT(s) AS count " +
            "FROM StudentDetails s " +
            "GROUP BY s.gender")
    List<Object[]> countByGenderRaw();
    default Map<String, Long> countByGender() {
        return countByGenderRaw().stream()
                .collect(Collectors.toMap(arr -> ((Gender) arr[0]).name(), arr -> (Long) arr[1]));
    }

    // Average Grade 12 result.
    @Query("SELECT AVG(s.grade12Result) FROM StudentDetails s WHERE s.grade12Result IS NOT NULL")
    Optional<Double> getAverageGrade12Result();

    // Count passed exit exams.
        @Query("SELECT COUNT(s) FROM StudentDetails s WHERE s.isStudentPassExitExam = 'Yes'")
    long countByIsStudentPassExitExamTrue();

    long countByStudentRecentStatus(StudentStatus status);

    long countByDocumentStatus(DocumentStatus status);

    long countByDepartmentEnrolled(Department department);


    // ==================== NEW METHODS FOR Registrar DASHBOARD ====================

    // Returns list of department name + count for dashboard
    @Query("SELECT d.deptName AS departmentName, COUNT(s) AS count " +
            "FROM StudentDetails s " +
            "JOIN s.departmentEnrolled d " +
            "GROUP BY d.deptName")
    List<DepartmentCountProjection> countStudentsByDepartment();


    interface DepartmentCountProjection {
        String getDepartmentName();
        Long getCount();
    }

    // Returns list of modality + count
    @Query("SELECT pm.modality AS modality, COUNT(s) AS count " +
            "FROM StudentDetails s " +
            "JOIN s.programModality pm " +
            "GROUP BY pm.modality")
    List<ProgramModalityCountProjection> countStudentsByProgramModality();

    interface ProgramModalityCountProjection {
        String getModality();
        Long getCount();
    }

    // Returns list of recent status + count
    @Query("SELECT s.studentRecentStatus.statusName AS status, COUNT(s) AS count " +
            "FROM StudentDetails s " +
            "GROUP BY s.studentRecentStatus.statusName")
    List<StudentStatusCountProjection> countStudentsByRecentStatus();

    interface StudentStatusCountProjection {
        String getStatus();
        Long getCount();
    }

    // Count students with impairment (not null)
    long countByImpairmentIsNotNull();

    // Counts registered students by gender
    long countByGender(Gender gender);

    // Count distinct departments (for department overview)
    @Query("SELECT COUNT(DISTINCT s.departmentEnrolled) FROM StudentDetails s")
    long countDistinctDepartments();

    long countByGenderAndStudentRecentStatus(Gender gender, StudentStatus status);

    // Projection to fetch only enrollment dates (dateEnrolledGC) – avoids loading full entities
    @Query("SELECT s.dateEnrolledGC FROM StudentDetails s WHERE s.dateEnrolledGC IS NOT NULL")
    List<LocalDate> findAllEnrollmentDates();


    // StudentDetailsRepository
    @Query("SELECT sd FROM StudentDetails sd " +
            "JOIN FETCH sd.user u " +
            "JOIN FETCH sd.departmentEnrolled " +
            "JOIN FETCH sd.studentRecentStatus " +
            "JOIN FETCH sd.batchClassYearSemester " +
            "WHERE u.id = :userId")
    Optional<StudentDetails> findWithUserAndDepartmentAndStatusAndBcys(@Param("userId") Long userId);
}

