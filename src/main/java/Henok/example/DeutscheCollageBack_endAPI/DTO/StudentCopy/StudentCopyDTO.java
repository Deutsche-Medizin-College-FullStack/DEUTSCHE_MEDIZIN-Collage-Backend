package Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for student copy (transcript) response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentCopyDTO {
    // Student Information
    private String idNumber; // username from user table
    private String fullName;
    private String gender;
    
    // Program Information
    private ProgramModalityInfo programModality;
    private ProgramLevelInfo programLevel;
    private LocalDate dateEnrolledGC;
    private DepartmentInfo department;
    private LocalDate dateOfBirthGC;
    
    // Academic Context
    private String studentBCYS;
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
    
    // Nested DTOs for structured information
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgramModalityInfo {
        private String id; // modalityCode
        private String name; // modality
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgramLevelInfo {
        private String id; // code
        private String name;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentInfo {
        private Long id; // dptID
        private String name; // deptName
    }
    
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

