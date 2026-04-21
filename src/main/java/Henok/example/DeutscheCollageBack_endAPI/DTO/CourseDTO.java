package Henok.example.DeutscheCollageBack_endAPI.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseDTO {

    @JsonProperty("cTitle")
    private String cTitle;

    @JsonProperty("cCode")
    private String cCode;

    @JsonProperty("theoryHrs")
    private Integer theoryHrs;

    @JsonProperty("labHrs")
    private Integer labHrs;

    @JsonProperty("cCategoryID")
    private Long cCatagoryID;

    @JsonProperty("departmentID")
    private Long departmentID;

    @JsonProperty("prerequisiteIds")
    private List<Long> prerequisiteIds;

    @JsonProperty("classYearID")
    private Long classYearID;

    @JsonProperty("semesterID")
    private String semesterID;

    @JsonProperty("isPassFail")
    private Boolean isPassFail;
}