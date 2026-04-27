package Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin;

import Henok.example.DeutscheCollageBack_endAPI.DTO.AssignTeacherCoursesRequest;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Gender;
import Henok.example.DeutscheCollageBack_endAPI.Enums.MaritalStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeacherRegisterRequest {


    @Size(max = 50)
    private String username;

    @Size(min = 6, max = 128)
    private String password;

    @Size(max = 100)
    private String firstNameAmharic;

    @Size(max = 100)
    private String lastNameAmharic;

    @Size(max = 100)
    private String firstNameEnglish;

    @Size(max = 100)
    private String lastNameEnglish;

    private Gender gender;
    private LocalDate dateOfBirthGC;

    @Size(max = 20)
    private String dateOfBirthEC;

    @Size(max = 15)
    private String phoneNumber;

    @Size(max = 100)
    private String email;

    @Digits(integer = 19, fraction = 0)
    private Long departmentId;

    private LocalDate hireDateGC;

    @Size(max = 20)
    private String hireDateEC;

    private LocalDate resignedDate;

    @Size(max = 50)
    private String title; // optional

    @Max(100)
    private Integer yearsOfExperience;

    @Size(max = 50)
    private String impairmentCode;

    private MaritalStatus maritalStatus;

    @Size(max = 20)
    private String currentAddressWoredaCode;

    @Size(max = 20)
    private String currentAddressZoneCode;

    @Size(max = 20)
    private String currentAddressRegionCode;

    // New: List of course + BCYS assignments
    private List<AssignTeacherCoursesRequest> courseAssignments = new ArrayList<>(); // optional
}