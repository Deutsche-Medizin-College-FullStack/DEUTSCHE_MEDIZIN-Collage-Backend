package Henok.example.DeutscheCollageBack_endAPI.DTO.Students;

import lombok.Data;

@Data
public class StudentBulkAcademicUpdateDTO {
    private Long studentId;
    private Long batchClassYearSemesterId;
    private Long studentRecentStatusId;
    private Long departmentEnrolledId;
    private Long batchId;
    private String accountStatus; // ENABLED or DISABLED
}
