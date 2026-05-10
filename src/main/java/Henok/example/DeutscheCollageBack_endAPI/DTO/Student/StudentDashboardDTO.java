package Henok.example.DeutscheCollageBack_endAPI.DTO.Student;

import Henok.example.DeutscheCollageBack_endAPI.DTO.Students.RemainingCourseDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Students.TakenCourseDTO;
import Henok.example.DeutscheCollageBack_endAPI.Enums.ExitExamPassStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentDashboardDTO {
    private StudentProfileSummary profileSummary;
    private AcademicProgressSnapshot academicProgress;
    private CourseProgress courseProgress;
    private DocumentStatusInfo documentStatus;
    private ExitExamAndGraduationInfo exitExamAndGraduation;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentProfileSummary {
        private Long studentId;
        private String fullName;
        private String department;
        private String programModality;
        private String currentClassYear;
        private String currentSemester;
        private String academicStatus;
        private byte[] profilePhoto;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AcademicProgressSnapshot {
        private Integer totalCompletedCreditHours;
        private Double currentCGPA;
        private Double lastSemesterGPA;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseProgress {
        private List<TakenCourseDTO> takenCourses;
        private int totalTakenCourses;
        private int totalTakenCreditHours;
        private List<RemainingCourseDTO> remainingCourses;
        private int totalRemainingCourses;
        private int totalRemainingCreditHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentStatusInfo {
        private String registrationDocumentStatus;
        private String studentPhotoUploadStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExitExamAndGraduationInfo {
        private String exitExamUserID;
        private Double exitExamScore;
        private ExitExamPassStatus isStudentPassExitExam;
        private Double grade12Result;
        private String yearOfExamG12;
        private String nationalexamIdG12;
        private LocalDate dateClassEndGC;
        private LocalDate dateGraduated;
        private String entryYearGC;
        private String entryYearEC;
    }
}

