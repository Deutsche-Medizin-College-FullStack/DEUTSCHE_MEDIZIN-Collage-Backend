package Henok.example.DeutscheCollageBack_endAPI.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentCGPADTO {
    private Long studentId;
    private Long studentUserId;
    private String idNumber;
    private String fullName;
    private String department;
    private Long batchId;
    private String batchName;
    private String batchClassYearSemester;
    private String studentStatus;
    private Double cgpa;
    private int completedCredits;
    private int numberOfCoursesTaken;
}
