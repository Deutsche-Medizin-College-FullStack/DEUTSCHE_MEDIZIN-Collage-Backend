package Henok.example.DeutscheCollageBack_endAPI.DTO.Reports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcademicSummaryBulkResponseDTO {
    private List<Item> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private AcademicSummaryRequestDTO request;
        private AcademicSummaryResponseDTO summary;
        private boolean success;
        private String error;
    }
}