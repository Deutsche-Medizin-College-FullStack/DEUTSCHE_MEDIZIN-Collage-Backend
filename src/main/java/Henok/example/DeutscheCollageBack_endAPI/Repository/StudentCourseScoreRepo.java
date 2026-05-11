package Henok.example.DeutscheCollageBack_endAPI.Repository;

import Henok.example.DeutscheCollageBack_endAPI.DTO.Registrar.RegistrarDashboardDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Semester;
import Henok.example.DeutscheCollageBack_endAPI.Service.TeacherService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public interface StudentCourseScoreRepo extends JpaRepository<StudentCourseScore, Long>, JpaSpecificationExecutor<StudentCourseScore> {

    /**
     * Checks if a score record already exists for the exact combination of:
     * - student
     * - course
     * - batch/class/year/semester
     * - course source
     *
     * Why this specific combination?
     * In the college system, a student can take the same course only once
     * within a specific batch/semester and source (e.g., regular, summer, retake).
     * This composite uniqueness prevents duplicate score entries during bulk import
     * and protects data integrity.
     *
     * @param student                the User entity representing the student
     * @param course                 the Course entity
     * @param batchClassYearSemester the BatchClassYearSemester entity
     * @param courseSource           the CourseSource entity
     * @return true if a record with this exact combination already exists
     */
    boolean existsByStudentAndCourseAndBatchClassYearSemesterAndCourseSource(
            User student,
            Course course,
            BatchClassYearSemester batchClassYearSemester,
            CourseSource courseSource);

    boolean existsByStudentAndCourseAndBatchClassYearSemester(User student, Course course, BatchClassYearSemester batchClassYearSemester);

    /**
     * Checks if there is already a record for the given student + course
     * in the same (classYear + semester) combination.
     *
     * Uses JPQL → parameters are safely bound (no SQL injection risk)
     */
    @Query("""
        SELECT CASE 
                 WHEN COUNT(s) > 0 THEN true 
                 ELSE false 
               END
        FROM StudentCourseScore s
        WHERE s.student = :student
          AND s.course = :course
          AND s.batchClassYearSemester.classYear = :classYear
          AND s.batchClassYearSemester.semester = :semester
    """)
    boolean existsByStudentAndCourseAndClassYearAndSemester(
            @Param("student")   User student,
            @Param("course")    Course course,
            @Param("classYear") ClassYear classYear,
            @Param("semester")  Semester semester
    );


    /**
     * Finds one existing record (if any) for the given student + course
     * in the same (classYear + semester) combination.
     *
     * Used mainly during updates to exclude the current record itself
     * when checking for duplicates after a proposed change.
     *
     * Returns Optional → safe when no record exists.
     * Uses JPQL → parameters are safely bound.
     */
    @Query("""
        SELECT s 
        FROM StudentCourseScore s
        WHERE s.student = :student
          AND s.course = :course
          AND s.batchClassYearSemester.classYear = :classYear
          AND s.batchClassYearSemester.semester = :semester
    """)
    Optional<StudentCourseScore> findFirstByStudentAndCourseAndClassYearAndSemester(
            @Param("student")   User student,
            @Param("course")    Course course,
            @Param("classYear") ClassYear classYear,
            @Param("semester")  Semester semester
    );


    Optional<StudentCourseScore> findByStudentAndCourseAndBatchClassYearSemester(User student, Course course, BatchClassYearSemester batchClassYearSemester);

    List<StudentCourseScore> findByStudentAndIsReleasedTrue(User student);

    /**
     * Finds all student course scores for a specific student and batch-class-year-semester.
     * Uses JOIN FETCH to eagerly load necessary relationships and avoid circular reference issues.
     *
     * @param student                The student
     * @param batchClassYearSemester The batch-class-year-semester
     * @return List of StudentCourseScore
     */
    @Query("SELECT DISTINCT scs FROM StudentCourseScore scs " +
            "JOIN FETCH scs.course " +
            "JOIN FETCH scs.batchClassYearSemester bcys " +
            "JOIN FETCH bcys.classYear " +
            "JOIN FETCH bcys.semester " +
            "WHERE scs.student = :student AND scs.batchClassYearSemester = :batchClassYearSemester")
    List<StudentCourseScore> findByStudentAndBatchClassYearSemester(@Param("student") User student, @Param("batchClassYearSemester") BatchClassYearSemester batchClassYearSemester);



    // Custom query to find enrollments for a set of (course, bcys) pairs
    // Why: Avoids N+1 and efficiently gets all students taking teacher's courses
    @Query("""
            SELECT scs FROM StudentCourseScore scs
            WHERE (scs.course, scs.batchClassYearSemester) IN :pairs
            """)
    List<StudentCourseScore> findByCourseAndBatchClassYearSemesterIn(@Param("pairs") Set<TeacherService.CourseBcysPair> pairs);

    // Simpler and safer: find by teacher (via TeacherCourseAssignment IDs)
    @Query("SELECT scs FROM StudentCourseScore scs " +
            "JOIN TeacherCourseAssignment tca ON " +
            "scs.course = tca.course AND scs.batchClassYearSemester = tca.bcys " +
            "WHERE tca.id IN :assignmentIds")
    List<StudentCourseScore> findByTeacherAssignmentIds(@Param("assignmentIds") List<Long> assignmentIds);

    //============================== Teacher dashboard related queries ==============================//
    // Counts distinct students for a teacher's assignments
    // Why: Joins to TeacherCourseAssignment to get all enrollments for the teacher's courses/BCYS
    // Returns 0 if no matches
    @Query("SELECT COUNT(DISTINCT scs.student) FROM StudentCourseScore scs " +
            "JOIN TeacherCourseAssignment tca ON " +
            "scs.course = tca.course AND scs.batchClassYearSemester = tca.bcys " +
            "WHERE tca.teacher = :teacher")
    int countDistinctStudentsByTeacher(TeacherDetail teacher);

    // Counts students per specific course + BCYS
    // Why: For per-course student count in dashboard
    // Returns 0 if no matches
    long countByCourseAndBatchClassYearSemester(Course course, BatchClassYearSemester bcys);

    /**
     * Counts how many course score records exist for this student.
     * This represents the total number of courses the student has taken/been registered for.
     */
    long countByStudent(User student);


    // Finds all enrollments for a specific course + BCYS combination
    // Why: Used to get the exact student list for one course the teacher is teaching
    List<StudentCourseScore> findByCourseAndBatchClassYearSemester(Course course, BatchClassYearSemester bcys);

    // New: Returns List<Object[]> with [deptName (String), avgScore (Double)]
    // Why: Safe projection; avoids Map direct return and resolves path issue by correct join order.
    @Query("SELECT d.deptName, AVG(scs.score) " +
            "FROM StudentDetails sd " +
            "JOIN sd.user u " +                     // sd -> user (existing OneToOne)
            "JOIN StudentCourseScore scs ON scs.student = u " +  // join scores for that user
            "JOIN sd.departmentEnrolled d " +
            "WHERE scs.isReleased = true " +
            "GROUP BY d.deptName")
    List<Object[]> findRawAverageScoresByDepartment();

    /**
     * Finds students with average score below threshold, but ONLY those whose current status is ACTIVE
     * (case-insensitive comparison on statusName)
     *
     * Returns raw projection: [username (String), fullName (String), avgScore (Double)]
     */
    @Query("SELECT u.username, CONCAT(sd.firstNameENG, ' ', sd.fatherNameENG), AVG(scs.score) " +
            "FROM StudentCourseScore scs " +
            "JOIN scs.student u " +
            "JOIN StudentDetails sd ON sd.user = u " +
            "WHERE scs.isReleased = true " +
            "AND UPPER(sd.studentRecentStatus.statusName) = 'ACTIVE' " +  // case-insensitive
            "GROUP BY u.username, sd.firstNameENG, sd.fatherNameENG " +
            "HAVING AVG(scs.score) < :threshold")
    List<Object[]> findRawLowAverageActiveStudents(@Param("threshold") Double threshold);

    List<StudentCourseScore> findByStudent(User student);



    @Query("SELECT scs FROM StudentCourseScore scs " +
            "JOIN FETCH scs.course c " +
            "JOIN FETCH scs.courseSource cs " +
            "JOIN FETCH scs.batchClassYearSemester bcys " +
            "WHERE scs.student.id = :studentId")
    List<StudentCourseScore> findByStudentWithCourseAndSourceAndBcys(@Param("studentId") Long studentId);

    /**
     * Finds all released student course scores for a specific BCYS.
     * Used for academic summary reports to get all students in a given BCYS.
     *
     * @param batchClassYearSemester The BCYS
     * @return List of released StudentCourseScore records
     */
    List<StudentCourseScore> findByBatchClassYearSemesterAndIsReleasedTrue(BatchClassYearSemester batchClassYearSemester);
}