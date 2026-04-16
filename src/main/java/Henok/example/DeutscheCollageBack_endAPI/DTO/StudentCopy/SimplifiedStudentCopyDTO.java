package Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Simplified DTO for student copy used in grade reports.
 * Excludes student information that is already at the grade report level.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimplifiedStudentCopyDTO {
    // Academic Context
    private ClassYearInfo classyear;
    private SemesterInfo semester;
    private AcademicYearInfo academicYear;
    
    // Course Grades
    private List<CourseGradeDTO> courses;
    
    // GPA Information
    private Double semesterGPA;
    private Double semesterCGPA;
    private String semesterGPALetter;
    private String semesterCGPALetter;
    private Integer previousCredit;
    private Double previousGradePoint;
    private Double previousCGPA;
    private String previousCGPALetter;
    private String status; // "PASSED" or "FAILED"
    
    // Nested DTOs
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassYearInfo {
        private Long id;
        private String name; // classYear
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemesterInfo {
        private String id; // academicPeriodCode (or semester ID)
        private String name; // academicPeriodName or similar
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AcademicYearInfo {
        private String yearCode;
        private String yearGC; // academicYearGC
    }
}
