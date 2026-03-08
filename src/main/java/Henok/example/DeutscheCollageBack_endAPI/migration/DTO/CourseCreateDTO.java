package Henok.example.DeutscheCollageBack_endAPI.migration.DTO;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

// CourseCreateDTO.java
// DTO for bulk course import. Uses codes for prerequisites to handle dependencies flexibly.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseCreateDTO {

    // Required: Course title
    private String title;

    // Required: Unique course code
    private String code;

    // Optional: Theory hours (defaults to 0 if null)
    private Integer theoryHrs;

    // Optional: Lab hours (defaults to 0 if null)
    private Integer labHrs;

    // Required: Course category ID
    private Long categoryId;

    // Optional: Department ID (can be null)
    private Long departmentId;

    // Optional: Set of prerequisite course codes (can be empty or null)
    // Why codes instead of IDs: Allows referencing both existing courses and courses in the same batch.
    private Set<String> prerequisiteCodes;

    // Optional: Class year ID (can be null)
    private Long classYearId;

    // Optional: Semester ID (can be null)
    private String semesterId;

    // Treat empty string as null for Long fields
    @JsonSetter("departmentId")
    public void setDepartmentId(String value) {
        this.departmentId = parseLongOrNull(value);
    }

    @JsonSetter("classYearId")
    public void setClassYearId(String value) {
        this.classYearId = parseLongOrNull(value);
    }

    // Helper method (can be static or in a util class)
    private Long parseLongOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;  // or throw exception if you prefer strict behavior
        }
    }
}