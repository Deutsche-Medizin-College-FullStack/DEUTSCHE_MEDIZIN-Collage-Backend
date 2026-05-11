package Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for course grade information in student copy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseGradeDTO {
    private String courseCode;
    private String courseTitle;
    private Integer totalCrHrs; // lecture + lab
    private Double score;
    private String letterGrade;
    private Double gradePoint; // totalCrHr * givenValue
}

