package Henok.example.DeutscheCollageBack_endAPI.DTO.Students;

import Henok.example.DeutscheCollageBack_endAPI.Enums.ExitExamPassStatus;
import lombok.Data;

import java.time.LocalDate;

// DTO to hold all StudentDetails fields, including photo and document
// Why: Prevents exposing entity internals, provides all data for retrieval
@Data
public class StudentDetailsDTO {

    private Long id;
    private Long userId;
    private String username;

    // Personal Info
    private String firstNameAMH;
    private String firstNameENG;
    private String fatherNameAMH;
    private String fatherNameENG;
    private String grandfatherNameAMH;
    private String grandfatherNameENG;
    private String motherNameAMH;
    private String motherNameENG;
    private String motherFatherNameAMH;
    private String motherFatherNameENG;

    private String gender;
    private Integer age;
    private String phoneNumber;
    private String dateOfBirthEC;
    private LocalDate dateOfBirthGC;

    // Place of Birth - readable names
    private String placeOfBirthWoredaCode;
    private String placeOfBirthWoredaName;
    private String placeOfBirthZoneCode;
    private String placeOfBirthZoneName;
    private String placeOfBirthRegionCode;
    private String placeOfBirthRegionName;

    // Current Address - readable names
    private String currentAddressWoredaCode;
    private String currentAddressWoredaName;
    private String currentAddressZoneCode;
    private String currentAddressZoneName;
    private String currentAddressRegionCode;
    private String currentAddressRegionName;

    private String email;
    private String maritalStatus;
    private String impairmentCode;
    private String impairmentDescription; // if available

    private Long schoolBackgroundId;
    private String schoolBackgroundName;

    private byte[] studentPhoto;

    // Emergency Contact
    private String contactPersonFirstNameAMH;
    private String contactPersonFirstNameENG;
    private String contactPersonFullNameENG;
    private String contactPersonLastNameAMH;
    private String contactPersonLastNameENG;
    private String contactPersonPhoneNumber;
    private String contactPersonRelation;

    // Academic Info
    private String dateEnrolledEC;
    private LocalDate dateEnrolledGC;

    private Long batchClassYearSemesterId;
    private String batchClassYearSemesterName; // e.g., "2024/2025 - Year 1 - Semester 1"

    private Long studentRecentStatusId;
    private String studentRecentStatusName;     // e.g., "Active", "Suspended"

    private Long departmentEnrolledId;
    private String departmentEnrolledName;     // e.g., "Computer Science"

    private Long batchId;
    private String batchName;

    private String programModalityCode;
    private String programModalityName;        // e.g., "Regular", "Extension"

    private String academicYearCode;
    private String academicYearGC;
    private String academicYearEC;

    // Document & Others
    private byte[] document;
    private String documentStatus;
    private String remark;
    private Boolean isTransfer;
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
