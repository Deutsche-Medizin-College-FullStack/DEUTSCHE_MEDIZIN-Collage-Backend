package Henok.example.DeutscheCollageBack_endAPI.Controller;

import Henok.example.DeutscheCollageBack_endAPI.DTO.DeanAndVice_Dean.*;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCGPADTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.User;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Service.DeanViceDeanService;
import Henok.example.DeutscheCollageBack_endAPI.Service.StudentDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vice-deans")
@RequiredArgsConstructor
public class ViceDeanController {

    private final DeanViceDeanService deanViceDeanService;
    private final StudentDetailService studentDetailService;


    // -----------[Get All Active Vice-Deans]------------------
    @GetMapping("/active")
    public ResponseEntity<?> getAllActiveViceDeans() {
        try {
            List<DeanViceDeanListDTO> viceDeans = deanViceDeanService.getAllActiveByRole(Role.VICE_DEAN);
            return ResponseEntity.ok(viceDeans);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "An unexpected error occurred while fetching active Vice-Deans");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    //Retrieves full details of a specific Vice-Dean by their details ID.
    //Same structure and fields as Get Dean by ID.
    @GetMapping("/{id}")
    public ResponseEntity<?> getViceDeanById(@PathVariable Long id) {
        try {
            DeanViceDeanDetailDTO detail = deanViceDeanService.getDetailById(id, Role.VICE_DEAN);
            return ResponseEntity.ok(detail);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "An unexpected error occurred");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PatchMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateMyProfile(
            @AuthenticationPrincipal User user,
            @RequestPart("data") DeanViceDeanUpdateRequest request,
            @RequestPart(name = "photograph", required = false) MultipartFile photograph) {
        
        try {
            if (user.getRole() != Role.VICE_DEAN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
            }
            
            deanViceDeanService.updateSelf(user, request, photograph);
            return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
            
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "An unexpected error occurred during update");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ==================== GET FILES ====================
    @GetMapping(value = "/get-photo/{id}", produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> getViceDeanPhoto(@PathVariable Long id) {
        try {
            byte[] photo = deanViceDeanService.getDeanViceDeanPhoto(id, Role.VICE_DEAN);

            if (photo == null || photo.length == 0) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("error", "Photo not found for Vice-Dean ID: " + id));
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(photo);

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "Failed to retrieve photo"));
        }
    }

    @GetMapping(value = "/get-document/{id}")
    public ResponseEntity<?> getDocumentById(@PathVariable Long id) {
        try {
            byte[] doc = deanViceDeanService.getDeanViceDeanDocument(id, Role.VICE_DEAN);
            if (doc == null || doc.length == 0) {
                Map<String, String> error = new HashMap<>(); 
                error.put("error", "Document not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vicedean_doc_" + id + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(doc);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>(); 
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>(); 
            error.put("error", "Failed to retrieve document");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // -----------[Update Vice-Dean]------------------
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateViceDean(
            @PathVariable Long id,
            @RequestPart("data") DeanViceDeanUpdateRequest request,
            @RequestPart(name = "photograph", required = false) MultipartFile photograph,
            @RequestPart(name = "document", required = false) MultipartFile document) {

        try {
            deanViceDeanService.updateDeanViceDean(id, request, photograph, document, Role.VICE_DEAN);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Vice-Dean updated successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "An unexpected error occurred during update");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // -----------[Get Profile]------------------
    @GetMapping("/profile")
    public ResponseEntity<?> getViceDeanProfile(@AuthenticationPrincipal User user) {
        try {
            if (user.getRole() != Role.VICE_DEAN) {
                throw new IllegalArgumentException("Access denied: Not a Vice-Dean");
            }
            DeanViceDeanProfileDTO profile = deanViceDeanService.getProfile(user);
            return ResponseEntity.ok(profile);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "An unexpected error occurred while fetching profile");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<?> getDocument(@PathVariable Long id) {
        return deanViceDeanService.getDocumentByIdAndRole(id, Role.DEAN);
    }

    // Retrieves comprehensive dashboard statistics and chart data for the authenticated Vice-Dean.
    // Provides the same metrics as the Dean dashboard: student totals, department info,
    // academic structure, enrollment trends, distributions, and additional academic indicators.
    // Accessible only to users with ROLE_VICE_DEAN (JWT required).
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@AuthenticationPrincipal User user) {
        try {
            // Role-based access control: only Vice-Deans can access this endpoint
            if (user.getRole() != Role.VICE_DEAN) {
                throw new IllegalArgumentException("Access denied: Not a Vice-Dean");
            }

            // Reuse the same dashboard data logic already implemented for Dean
            // Keeps code DRY and ensures both roles see identical metrics
            DeanDashboardDTO dashboard = deanViceDeanService.getDashboardData();

            return ResponseEntity.ok(dashboard);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "An unexpected error occurred while fetching dashboard data");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // -----------[Get All Students with CGPA]------------------
    @GetMapping("/get-all-students-cgpa")
    public ResponseEntity<?> getAllStudentsWithCGPA(@AuthenticationPrincipal User user) {
        try {
            if (user.getRole() != Role.VICE_DEAN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
            }
            List<StudentCGPADTO> students = studentDetailService.getAllStudentsWithCGPA();
            return ResponseEntity.ok(students);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "An unexpected error occurred while fetching student CGPAs");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
