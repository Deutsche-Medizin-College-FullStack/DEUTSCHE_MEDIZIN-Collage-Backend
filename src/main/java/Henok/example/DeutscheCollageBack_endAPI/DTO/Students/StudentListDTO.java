package Henok.example.DeutscheCollageBack_endAPI.DTO.Students;

import lombok.Getter;
import lombok.Setter;

// Lightweight DTO for student list view
@Getter
@Setter
public class StudentListDTO {
    private Long id;
    private String username;
    private String accountStatus;        // "ENABLED" or "DISABLED"
    private String firstNameAMH;
    private String firstNameENG;
    private String fatherNameAMH;
    private String fatherNameENG;
    private String grandfatherNameAMH;
    private String grandfatherNameENG;
    private Long studentRecentStatusId;
    private String studentRecentStatus;  // status name
    private Long departmentEnrolledId;
    private String departmentEnrolled;   // department name
    private Long batchId;
    private String batchName;
    private Long batchClassYearSemesterId;
    private String batchClassYearSemester; // BCYS name
    private byte[] studentPhoto;
}