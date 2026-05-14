package Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy;

import Henok.example.DeutscheCollageBack_endAPI.Entity.GradingSystem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentCopyOptions {

    @Builder.Default
    private boolean includeStudentInfo = true;      // idNumber, fullName, gender, dateOfBirthGC

    @Builder.Default
    private boolean includeProgramInfo = true;      // Program Modality, Program Level, Date Enrolled, Department

    @Builder.Default
    private boolean includeAcademicContext = true;  //classyear, semester, academicYear, studentBCYS

    private GradingSystem gradingSystem;

    @Builder.Default
    private boolean includeGPA = true;              // semesterGPA, semesterCGPA, semesterGPALetter, semesterCGPALetter

    @Builder.Default
    private boolean calculateCGPA = true;           // Full CGPA calculation (heavy)

    @Builder.Default
    private boolean includePreviousTotals = true;   // previousCredit, previousGradePoint, previousCGPA, previousCGPALetter

    @Builder.Default
    private boolean includeStatus = true;

    public static StudentCopyOptions full() {
        return new StudentCopyOptions();
    }

    /**
     * Best for transcripts / grade reports: Academic context + GPA + pre-fetched GradingSystem
     */
    public static StudentCopyOptions forTranscript(GradingSystem gradingSystem) {
        return StudentCopyOptions.builder()
                .includeStudentInfo(false)
                .includeProgramInfo(false)
                .includeAcademicContext(true)      // ← Must be true
                .includeGPA(true)
                .calculateCGPA(true)
                .includePreviousTotals(true)
                .includeStatus(true)
                .gradingSystem(gradingSystem)
                .build();
    }

    /**
     * Optimized options for Academic Summary Report
     * Returns: courses, semesterGPA, semesterCGPA, status
     * Skips: letters, previousCGPA, student info, program info, academic context
     */
    public static StudentCopyOptions forAcademicSummary(GradingSystem gradingSystem) {
        return StudentCopyOptions.builder()
                .includeStudentInfo(false)
                .includeProgramInfo(false)
                .includeAcademicContext(false)
                .includeGPA(true)
                .calculateCGPA(true)
                .includePreviousTotals(false)   // Not needed as per your request
                .includeStatus(true)
                .gradingSystem(gradingSystem)
                .build();
    }
}