package Henok.example.DeutscheCollageBack_endAPI.DTO.DepartmentHead;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentTeacherDTO {
    private Long teacherId;
    private Long teacherUserId;
    private String fullName;
    private String title;
    private String email;
    private String phoneNumber;
    private Integer yearsOfExperience;
    private Long numberOfCourses;
    private String accountStatus; // "Active" or "Disabled"
}


