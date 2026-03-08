package Henok.example.DeutscheCollageBack_endAPI.DTO.Students;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import Henok.example.DeutscheCollageBack_endAPI.Enums.DocumentStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.ExitExamPassStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Gender;
import Henok.example.DeutscheCollageBack_endAPI.Enums.MaritalStatus;
import lombok.Data;

// StudentDetailsSummaryDTO.java
@Data
public class StudentDetailsSummaryDTO {
    private Long id;
    private String username;  // from User entity

    // Personal info
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

    private Gender gender;
    private Integer age;
    private String phoneNumber;
    private String dateOfBirthEC;
    private LocalDate dateOfBirthGC;

    // Place of birth
    private Map<String, Object> placeOfBirthWoreda;   // {id: "...", name: "..."}
    private Map<String, Object> placeOfBirthZone;
    private Map<String, Object> placeOfBirthRegion;

    // Current address
    private Map<String, Object> currentAddressWoreda;
    private Map<String, Object> currentAddressZone;
    private Map<String, Object> currentAddressRegion;

    private String email;
    private MaritalStatus maritalStatus;

    private Map<String, Object> impairment;           // {code: "...", name: "..."}
    private Map<String, Object> schoolBackground;     // assuming it has id & name

    // Emergency contact
    private String contactPersonFirstNameAMH;
    private String contactPersonFirstNameENG;
    private String contactPersonLastNameAMH;
    private String contactPersonLastNameENG;
    private String contactPersonPhoneNumber;
    private String contactPersonRelation;

    // Academic info
    private String dateEnrolledEC;
    private LocalDate dateEnrolledGC;

    private Map<String, Object> academicYear;         // {yearCode: "...", name: "..."}
    private Map<String, Object> batch;                // {id: ..., name: "..."}
    private Map<String, Object> batchClassYearSemester;// {id: ..., name: "..."}
    private Map<String, Object> recentBatch;  // {id: Long, name: String}
    private Map<String, Object> studentRecentStatus;  // assuming it has id & name
    private Map<String, Object> departmentEnrolled;   // {id: ..., name: "..."}
    private Map<String, Object> programModality;      // {code: "...", name: "..."}
    private Map<String, Object> programLevel;      // {code: "...", name: "..."}

    
    private DocumentStatus documentStatus;
    private Double grade12Result;
    private String remark;
    private boolean isTransfer;
    private String exitExamUserID;
    private Double exitExamScore;
    private ExitExamPassStatus isStudentPassExitExam;
    private String yearOfExamG12;
    private String nationalexamIdG12;
    private LocalDate dateClassEndGC;
    private LocalDate dateGraduated;
    private String entryYearGC;
    private String entryYearEC;

    // New fields - stored directly in StudentDetails table
    private BigDecimal cgpa;                  // e.g. 3.45
    private Integer totalEarnedCreditHours;   // e.g. 92
    private Integer totalCoursesRegistered;       // e.g.  thirty

}