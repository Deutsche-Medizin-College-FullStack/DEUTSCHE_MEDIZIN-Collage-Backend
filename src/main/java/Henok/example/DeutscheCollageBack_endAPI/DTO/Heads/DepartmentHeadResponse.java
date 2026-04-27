package Henok.example.DeutscheCollageBack_endAPI.DTO.Heads;

import Henok.example.DeutscheCollageBack_endAPI.Enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

// DepartmentHeadResponse DTO (clean response with usernames and simplified FKs)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentHeadResponse {

    private Long id;
    private Long userId;                       // from User
    private String username;                     // from User
    private String firstNameENG;
    private String firstNameAMH;
    private String fatherNameENG;
    private String fatherNameAMH;
    private String grandfatherNameENG;
    private String grandfatherNameAMH;
    private Gender gender;
    private String phoneNumber;
    private String email;
    private LocalDate hiredDateGC;
    private String hiredDateEC;
    private Map<String, Object> department;     // { "id": ..., "name": ... }
    private Map<String, Object> residenceRegion; // { "id": regionCode, "name": region }
    private Map<String, Object> residenceZone;   // { "id": zoneCode, "name": zone }
    private Map<String, Object> residenceWoreda; // { "id": woredaCode, "name": woreda }
    private String remark;
    private boolean hasPhoto;
    private boolean hasDocument;
    private boolean isActive;
    private String accountStatus; // "Active" or "Disabled"
}
