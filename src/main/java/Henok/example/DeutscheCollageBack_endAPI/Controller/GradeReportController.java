package Henok.example.DeutscheCollageBack_endAPI.Controller;

import Henok.example.DeutscheCollageBack_endAPI.DTO.GradeReport.GradeReportRequestDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.GradeReport.GradeReportResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.Error.ErrorResponse;
import Henok.example.DeutscheCollageBack_endAPI.Service.GradeReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for grade report operations.
 */
@RestController
@RequestMapping("/api/grade-report")
public class GradeReportController {

    @Autowired
    private GradeReportService gradeReportService;

    /**
     * Generates grade reports for multiple students.
     * 
     * @param request The request containing list of student IDs
     * @return GradeReportResponseDTO containing grade reports for all valid students
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateGradeReports(@RequestBody GradeReportRequestDTO request) {
        try {
            if (request.getStudentIds() == null || request.getStudentIds().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Student IDs list cannot be empty"));
            }

            GradeReportResponseDTO response = gradeReportService.generateGradeReports(request);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(resolveIllegalStateMessage(e)));
        } catch (Exception e) {
            IllegalStateException illegalState = findIllegalStateCause(e);
            if (illegalState != null) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse(resolveIllegalStateMessage(illegalState)));
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate grade reports: " + e.getMessage()));
        }
    }

    private IllegalStateException findIllegalStateCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IllegalStateException) {
                return (IllegalStateException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private String resolveIllegalStateMessage(IllegalStateException exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() instanceof IllegalStateException) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return (message == null || message.isBlank())
                ? "Failed to generate grade reports due to an invalid internal state"
                : message;
    }
}

