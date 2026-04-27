package Henok.example.DeutscheCollageBack_endAPI.DTO;

import Henok.example.DeutscheCollageBack_endAPI.Enums.Gender;
import Henok.example.DeutscheCollageBack_endAPI.Enums.MaritalStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
public class TeacherResponseDTO {

    private Long teacherId;
    private Long userId;
    private String username;

    private String firstNameAmharic;
    private String lastNameAmharic;
    private String firstNameEnglish;
    private String lastNameEnglish;

    private Gender gender;
    private LocalDate dateOfBirthGC;
    private String dateOfBirthEC;

    private String phoneNumber;
    private String email;

    private String departmentName;
    private LocalDate hireDateGC;
    private String hireDateEC;
    private LocalDate resignedDate;
    private String title;
    private Integer yearsOfExperience;

    private String impairment;
    private String impairmentCode;
    private MaritalStatus maritalStatus;

    // --- Address fields (separated) ---
    private String woredaCode;      // e.g., "WOREDA001"
    private String woredaName;      // e.g., "Addis Ababa Woreda 1"
    private String zoneCode;        // e.g., "ZONE01"
    private String zoneName;        // e.g., "Zone 1"
    private String regionCode;      // e.g., "REG001"
    private String regionName;      // e.g., "Addis Ababa"

    private String photographBase64;   // Base64-encoded image

    private List<AssignedCourseDTO> assignedCourses;
}