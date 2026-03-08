package Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin;

import Henok.example.DeutscheCollageBack_endAPI.Enums.ExitExamPassStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AcceptApplicationRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 50)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 128)
    private String password;

    private String dateEnrolledEC;

    @NotNull(message = "Enrollment date (GC) is required")
    private LocalDate dateEnrolledGC;

    private String academicYearCode;

    @NotNull(message = "Batch ID is required")
    private Long batchId;

    @NotNull(message = "Batch class year semester ID is required")
    private Long batchClassYearSemesterId;

    @NotNull(message = "Student recent status ID is required")
    private Long studentRecentStatusId;

    @NotNull(message = "Transfer status is required")
    private Boolean isTransfer;

    private String exitExamUserID;

    private Double exitExamScore;

    private ExitExamPassStatus isStudentPassExitExam;

    private Double grade12Result;

    private String remark;
}