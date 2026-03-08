package Henok.example.DeutscheCollageBack_endAPI.migration.DTO;

import lombok.Data;

@Data
public class StudentImportDTO {

    // User fields
    private String username;                   // "username/student-id" → e.g. DHMC-MD-02-12
    private String password;                   // usually empty → default "stud1234"

    // Personal names – English
    private String firstNameENG;
    private String fatherNameENG;
    private String grandfatherNameENG;

    // Personal names – Amharic
    private String firstNameAMH;
    private String fatherNameAMH;
    private String grandfatherNameAMH;

    // Mother names – not present in legacy data → will remain null
    private String motherNameAMH;
    private String motherNameENG;

    // Demographic
    private String gender;                     // "Male" or "Female" → mapped to Gender enum
    private String dateOfBirthGC;               // YYYY-MM-DD → used for age calculation and dateOfBirthGC
    private String maritalStatus;              // "Single", "Married", ... → mapped to MaritalStatus enum
    private String phoneNumber;                 // formatted string, unique in DB

    // Enrollment
    private String dateEnrolledGC;              // YYYY-MM-DD

    // Foreign keys provided as numeric IDs (Long in DB)
    private String departmentEnrolledId;        // e.g. "2" → Department.id
    private String batchId;                     // e.g. "5" → Batch.id
    private String batchClassYearSemesterId;    // e.g. "15" → BatchClassYearSemester.id
    private String studentRecentStatusId;      // e.g. "2" → StudentStatus.id
    private String schoolBackgroundId;         // e.g. "1" → SchoolBackground.id

    // Program modality – code provided
    private String programModalityCode;        // e.g. "RG"

    // Birth place – codes provided
    private String placeOfBirthRegionCode;     // e.g. "AMH"
    private String placeOfBirthZoneCode;       // e.g. "GOJW"
    private String placeOfBirthWoredaCode;     // e.g. "1199"

    // Emergency contact – mostly empty in sample
    private String contactPersonFullNameENG;
    private String contactPersonPhoneNumber;
    private String contactPersonRelation;

    // Other useful legacy fields
    private String remark;
    private String isTransfer;                 // "TRUE"/"FALSE"
    private String documentStatus;             // "TRUE" → COMPLETE, else INCOMPLETE
    private String exitExamUserID;
    private String exitExamScore;              // will be parsed to Double if present
    private String isStudentPassExitExam;      // "TRUE"/"FALSE"/"NOT_TAKEN"
    private String grade12Result;              // will be parsed to Double if present

    // NEW FIELDS ADDED
    private String Year_of_Exam_G12;           // e.g. "2025"
    private String NATIONALEXAM_ID_G12;        // e.g. "305288"
    private String Date_Class_EndGC;           // Date class ended
    private String Date_Graduated;             // Graduation date
    private String Entry_Year_GC;              // e.g. "2019/20 G.C"
    private String Entry_Year_EC;              // e.g. "2012 E.C"
}
