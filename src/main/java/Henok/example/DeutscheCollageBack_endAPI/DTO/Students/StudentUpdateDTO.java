package Henok.example.DeutscheCollageBack_endAPI.DTO.Students;

import Henok.example.DeutscheCollageBack_endAPI.Enums.DocumentStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.ExitExamPassStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Gender;
import Henok.example.DeutscheCollageBack_endAPI.Enums.MaritalStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudentUpdateDTO {
    // Personal Information (Names in Amharic and English)
    private String username; // Optional: Update linked user username
    private String firstNameAMH; // Optional: Update first name in Amharic
    private String firstNameENG; // Optional: Update first name in English
    private String fatherNameAMH; // Optional: Update father's name in Amharic
    private String fatherNameENG; // Optional: Update father's name in English
    private String grandfatherNameAMH; // Optional: Update grandfather's name in Amharic
    private String grandfatherNameENG; // Optional: Update grandfather's name in English
    private String motherNameAMH; // Optional: Update mother's name in Amharic
    private String motherNameENG; // Optional: Update mother's name in English
    private String motherFatherNameAMH; // Optional: Update mother's father name in Amharic
    private String motherFatherNameENG; // Optional: Update mother's father name in English

    // Demographic Information
    private Gender gender; // Optional: Update gender (MALE, FEMALE, OTHER)
    private Integer age; // Optional: Update age
    private String phoneNumber; // Optional: Update phone number (must remain unique)
    private String dateOfBirthEC; // Optional: Update date of birth in Ethiopian Calendar
    private LocalDate dateOfBirthGC; // Optional: Update date of birth in Gregorian Calendar

    // Place of Birth (Foreign Key Codes)
    private String placeOfBirthWoredaCode; // Optional: Update woreda code for place of birth
    private String placeOfBirthZoneCode; // Optional: Update zone code for place of birth
    private String placeOfBirthRegionCode; // Optional: Update region code for place of birth

    // Current Address (Foreign Key Codes)
    private String currentAddressWoredaCode; // Optional: Update woreda code for current address
    private String currentAddressZoneCode; // Optional: Update zone code for current address
    private String currentAddressRegionCode; // Optional: Update region code for current address

    // Additional Personal Information
    private String email; // Optional: Update email address
    private MaritalStatus maritalStatus; // Optional: Update marital status
    private String impairmentCode; // Optional: Update impairment code
    private Long schoolBackgroundId; // Optional: Update school background ID

    // Emergency Contact Information
    private String contactPersonFirstNameAMH; // Optional: Update emergency contact first name in Amharic
    private String contactPersonFirstNameENG; // Optional: Update emergency contact first name in English
    private String contactPersonLastNameAMH; // Optional: Update emergency contact last name in Amharic
    private String contactPersonLastNameENG; // Optional: Update emergency contact last name in English
    private String contactPersonFullNameENG; // Optional: Update full emergency contact name in English
    private String contactPersonPhoneNumber; // Optional: Update emergency contact phone number
    private String contactPersonRelation; // Optional: Update relation to student

    // Academic Information
    private String dateEnrolledEC; // Optional: Update enrollment date in Ethiopian Calendar
    private LocalDate dateEnrolledGC; // Optional: Update enrollment date in Gregorian Calendar
    private String academicYearCode;
    private Long batchClassYearSemesterId; // Optional: Update BatchClassYearSemester ID
    private Long studentRecentStatusId; // Optional: Update current student status ID
    private Long departmentEnrolledId; // Optional: Update department enrolled ID
    private Long batchId; // Optional: Update batch ID (nullable)
    private String programModalityCode; // Optional: Update program modality code

    // Document Information
    private DocumentStatus documentStatus; // Optional: Update document status
    private String remark; // Optional: Update remark for documents

    // Transfer and Exit Exam Information
    private Boolean isTransfer; // Optional: Update transfer status
    private String exitExamUserID; // Optional: Update exit exam user ID
    private Double exitExamScore; // Optional: Update exit exam score
    private ExitExamPassStatus isStudentPassExitExam; // Optional: Update pass/fail/not taken status for exit exam

    // Academic Performance
    private Double grade12Result; // Optional: Update Grade 12 exam result

    // Additional academic fields
    private String yearOfExamG12;
    private String nationalexamIdG12;
    private LocalDate dateClassEndGC;
    private LocalDate dateGraduated;
    private String entryYearGC;
    private String entryYearEC;
}