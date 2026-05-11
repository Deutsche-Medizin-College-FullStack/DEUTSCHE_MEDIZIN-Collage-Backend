package Henok.example.DeutscheCollageBack_endAPI.DTO.Reports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcademicSummaryRequestDTO {
    private Long departmentId;
    private Long bcysId;
}
