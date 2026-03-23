package Henok.example.DeutscheCollageBack_endAPI.migration.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentBCYSImportDTO {

    // All fields are String because Excel-to-JSON conversion usually produces strings
    // We will parse/convert them inside the service/controller

    private String bcysId;           // will parse to Long
    private String departmentId;     // will parse to Long
    private String academicYearCode; // can be null

    private String classStartGC;     // "2025-02-10" format → will parse to LocalDate
    private String classStartEC;     // "2017-05-30" format → remains String
    private String classEndGC;       // "2025-06-30" → LocalDate
    private String classEndEC;       // "2017-10-20" → String
}
