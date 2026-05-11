package Henok.example.DeutscheCollageBack_endAPI.Controller;

import Henok.example.DeutscheCollageBack_endAPI.DTO.Reports.AcademicSummaryRequestDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Reports.AcademicSummaryBulkResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Reports.AcademicSummaryResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.Error.ErrorResponse;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Service.AcademicSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/registrar/reports")
public class ReportsController {

    @Autowired
    private AcademicSummaryService academicSummaryService;

    /**
     * Generates an academic summary report for all students in a given department and BCYS.
     * 
     * @param departmentId Department ID
     * @param bcysId BatchClassYearSemester ID
     * @return AcademicSummaryResponseDTO with all students' academic data
     */
    @GetMapping("/academic-summary")
        public ResponseEntity<?> getAcademicSummary(
            @RequestParam Long departmentId,
            @RequestParam Long bcysId
    ) {
        try {
            AcademicSummaryRequestDTO request = new AcademicSummaryRequestDTO();
            request.setDepartmentId(departmentId);
            request.setBcysId(bcysId);
            System.out.println("Received request for academic summary with departmentId: " + departmentId + ", bcysId: " + bcysId);

            AcademicSummaryResponseDTO response = academicSummaryService.generateAcademicSummary(request);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate academic summary: " + e.getMessage()));
        }
    }

    /**
     * Generates academic summaries in bulk.
     * Each request is handled independently so one failing item does not fail the whole response.
     */
    @PostMapping("/academic-summary")
    public ResponseEntity<?> getAcademicSummaryBulk(
            @RequestBody List<AcademicSummaryRequestDTO> requests
    ) {
        try {
            System.out.println("Received bulk academic summary request with size: " + (requests == null ? 0 : requests.size()));
            AcademicSummaryBulkResponseDTO response = academicSummaryService.generateAcademicSummaries(requests);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate academic summaries: " + e.getMessage()));
        }
    }
}
