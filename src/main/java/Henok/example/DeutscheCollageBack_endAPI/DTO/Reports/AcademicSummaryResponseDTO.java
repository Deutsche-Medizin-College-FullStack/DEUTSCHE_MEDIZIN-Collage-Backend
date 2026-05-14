package Henok.example.DeutscheCollageBack_endAPI.DTO.Reports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcademicSummaryResponseDTO {
    private HeaderInfo header;
    private List<StudentSummary> students;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeaderInfo {
        private String departmentBcysDisplay;  // e.g., "Medicine - 1-1-S1"
        private String departmentName;
        private String departmentCode;
        private String batchName;           // e.g., "Batch 2020"
        private String classYearName;           // e.g., "1st Year"
        private String semesterName;            // e.g., "First Semester"
        private AcademicYearInfo academicYear;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AcademicYearInfo {
        private String yearCode;
        private String yearGC;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentSummary {
        private String studentId;  // username
        private List<CourseInfo> courses;
        private Double semesterGPA;
        private Double semesterCGPA;
        private String semesterGPALetter;
        private String semesterCGPALetter;
        private Double previousCGPA;
        private String previousCGPALetter;
        private String status;  // PASSED / FAILED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseInfo {
        private String courseCode;
        private String courseName;
        private Double score;
        private String letterGrade;
    }
}
