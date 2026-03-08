package Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin;

import Henok.example.DeutscheCollageBack_endAPI.Enums.DocumentStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.ExitExamPassStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Gender;
import Henok.example.DeutscheCollageBack_endAPI.Enums.MaritalStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentRegisterRequest {
    // User credentials for registration
    @Size(max = 50)
    private String username; // Required: Username for login

    @Size(min = 6, max = 128)
    private String password; // Required: Password for login

    // Personal Information (Names in Amharic and English)
    @Size(max = 100)
    private String firstNameAMH; // Required: First name in Amharic

    @Size(max = 100)
    private String firstNameENG; // Required: First name in English

    @Size(max = 100)
    private String fatherNameAMH; // Required: Father's name in Amharic

    @Size(max = 100)
    private String fatherNameENG; // Required: Father's name in English

    @Size(max = 100)
    private String grandfatherNameAMH; // Required: Grandfather's name in Amharic

    @Size(max = 100)
    private String grandfatherNameENG; // Required: Grandfather's name in English

    @Size(max = 100)
    private String motherNameAMH; // Required: Mother's name in Amharic

    @Size(max = 100)
    private String motherNameENG; // Required: Mother's name in English

    @Size(max = 100)
    private String motherFatherNameAMH; // Required: Mother's father name in Amharic

    @Size(max = 100)
    private String motherFatherNameENG; // Required: Mother's father name in English

    // Demographic Information
    private Gender gender; // Required: Gender (MALE, FEMALE, OTHER)

    @Max(150)
    private Integer age; // Required: Age of the student

    @Size(max = 15)
    private String phoneNumber; // Required: Unique phone number

    @Size(max = 20)
    private String dateOfBirthEC; // Required: Date of birth in Ethiopian Calendar

    private LocalDate dateOfBirthGC; // Required: Date of birth in Gregorian Calendar

    // Place of Birth (Foreign Key Codes)
    @Size(max = 20)
    private String placeOfBirthWoredaCode; // Required: Woreda code for place of birth

    @Size(max = 20)
    private String placeOfBirthZoneCode; // Required: Zone code for place of birth

    @Size(max = 20)
    private String placeOfBirthRegionCode; // Required: Region code for place of birth

    // Current Address (Foreign Key Codes)
    @Size(max = 20)
    private String currentAddressWoredaCode; // Required: Woreda code for current address

    @Size(max = 20)
    private String currentAddressZoneCode; // Required: Zone code for current address

    @Size(max = 20)
    private String currentAddressRegionCode; // Required: Region code for current address

    // Additional Personal Information
    @Size(max = 100)
    private String email; // Optional: Email address

    private MaritalStatus maritalStatus; // Required: Marital status (SINGLE, MARRIED, etc.)

    @Size(max = 50)
    private String impairmentCode; // Optional: Impairment code if applicable

    @Digits(integer = 19, fraction = 0)
    private Long schoolBackgroundId; // Required: School background ID

    // Emergency Contact Information
    @Size(max = 100)
    private String contactPersonFirstNameAMH; // Required: Emergency contact first name in Amharic

    @Size(max = 100)
    private String contactPersonFirstNameENG; // Required: Emergency contact first name in English

    @Size(max = 100)
    private String contactPersonLastNameAMH; // Required: Emergency contact last name in Amharic

    @Size(max = 100)
    private String contactPersonLastNameENG; // Required: Emergency contact last name in English

    @Size(max = 15)
    private String contactPersonPhoneNumber; // Required: Emergency contact phone number

    @Size(max = 50)
    private String contactPersonRelation; // Optional: Relation to student (e.g., Parent)

    // Academic Information
    @Size(max = 20)
    private String dateEnrolledEC; // Optional: Enrollment date in Ethiopian Calendar

    private LocalDate dateEnrolledGC; // Required: Enrollment date in Gregorian Calendar

    @Size(max = 20)
    private String academicYearCode; //optional: Academic year code

    @Digits(integer = 19, fraction = 0)
    private Long batchClassYearSemesterId; // Required: BatchClassYearSemester ID

    @Digits(integer = 19, fraction = 0)
    private Long studentRecentStatusId; // Required: Current student status ID

    @Digits(integer = 19, fraction = 0)
    private Long departmentEnrolledId; // Required: Department enrolled ID

    @Digits(integer = 19, fraction = 0)
    private Long batchId; // Optional: Batch ID (nullable)

    @Size(max = 20)
    private String programModalityCode; // Required: Program modality code (e.g., Regular)

    // Document Information
    private DocumentStatus documentStatus; // Optional: Defaults to INCOMPLETE

    @Size(max = 500)
    private String remark; // Optional: Notes for incomplete documents

    // Transfer and Exit Exam Information
    private Boolean isTransfer; // Optional: Indicates if student is a transfer

    @Size(max = 50)
    private String exitExamUserID; // Optional: Exit exam user ID

    @Digits(integer = 5, fraction = 2)
    private Double exitExamScore; // Optional: Exit exam score

    private ExitExamPassStatus isStudentPassExitExam; // Optional: Pass/fail/not taken status for exit exam

    // Academic Performance
    @Digits(integer = 5, fraction = 2)
    private Double grade12Result; // Optional: Grade 12 exam result
}