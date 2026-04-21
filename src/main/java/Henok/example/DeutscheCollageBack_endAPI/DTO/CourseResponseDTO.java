package Henok.example.DeutscheCollageBack_endAPI.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


// CourseResponseDTO.java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseResponseDTO {

    private Long id;
    private String cTitle;
    private String cCode;
    private Integer theoryHrs;
    private Integer labHrs;
    private Boolean isPassFail;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefDTO {
        private Long id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemesterRefDTO {
        private String code;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrerequisiteDTO {
        private Long id;
        private String cCode;
        private String name; // cTitle
    }

    private RefDTO courseCategory;
    private RefDTO department;
    private RefDTO classYear;
    private SemesterRefDTO semester;
    private List<PrerequisiteDTO> prerequisites;
}
