package Henok.example.DeutscheCollageBack_endAPI.Controller;

import Henok.example.DeutscheCollageBack_endAPI.DTO.GradeReport.GradeReportRequestDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.GradeReport.GradeReportResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Student.StudentDashboardDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Student.StudentProfileResponse;
import Henok.example.DeutscheCollageBack_endAPI.Entity.User;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Error.ErrorResponse;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.StudentDetailsRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.UserRepository;
import Henok.example.DeutscheCollageBack_endAPI.Service.GradeReportService;
import Henok.example.DeutscheCollageBack_endAPI.Service.StudentDashboardService;
import Henok.example.DeutscheCollageBack_endAPI.Service.StudentDetailService;
import Henok.example.DeutscheCollageBack_endAPI.Service.UserService;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
public class StudentPortalController {

    @Autowired
    private StudentDashboardService studentDashboardService;
    @Autowired
    private GradeReportService gradeReportService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentDetailsRepository studentDetailsRepository;
    @Autowired
    private StudentDetailService studentDetailsService;
    @Autowired
    private UserService userService;

    /**
     * Gets the student dashboard with all relevant information.
     * Requires JWT authentication and ROLE_STUDENT.
     * 
     * @return StudentDashboardDTO with profile, academic progress, courses, grades, and document status
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        try {
            // Get current user from security context
            Long userId = getCurrentUserId();
            
            // Verify user has STUDENT role
            verifyStudentRole(userId);
            
            // Get dashboard data
            StudentDashboardDTO dashboard = studentDashboardService.getStudentDashboard(userId);
            return ResponseEntity.ok(dashboard);
            
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Access denied: " + e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve dashboard: " + e.getMessage()));
        }
    }

    // ------------[Get Authenticated Student's Full Profile] --------
    @GetMapping("/profile")
    public ResponseEntity<?> getMyStudentProfile() {
        try {
            // Extract username from JWT token (authenticated user)
            String username = SecurityContextHolder.getContext().getAuthentication().getName();

            // Load the User entity first
            User user = (User) userService.loadUserByUsername(username);

            // Ensure the user has STUDENT role - extra safety layer
            if (user.getRole() != Role.STUDENT) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "error", "Access denied",
                                "message", "This endpoint is only for students"
                        ));
            }

            // Delegate to service to build full student profile DTO
            StudentProfileResponse response = studentDetailsService.getStudentProfileByUser(user);

            return ResponseEntity.ok(response);

        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Student profile not found"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load student profile"));
        }
    }

    /**
     * Gets grade reports for the authenticated student.
     * Uses the same grade report logic as registrars.
     * 
     * @return GradeReportResponseDTO containing the student's grade reports
     */
    @GetMapping("/grade-reports")
    public ResponseEntity<?> getGradeReports() {
        try {
            // Get current user from security context
            Long userId = getCurrentUserId();
            
            // Verify user has STUDENT role
            verifyStudentRole(userId);
            
            // Get student details to get student ID
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
            
            var studentDetails = studentDetailsRepository.findByUser(user)
                    .orElseThrow(() -> new ResourceNotFoundException("Student details not found for user id: " + userId));
            
            Long studentId = studentDetails.getId();
            
            // Generate grade report for this student
            GradeReportRequestDTO request = new GradeReportRequestDTO();
            request.setStudentIds(java.util.List.of(studentId));

            GradeReportResponseDTO response = gradeReportService.generateGradeReports(request);
            return ResponseEntity.ok(response);
            
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Access denied: " + e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve grade reports: " + e.getMessage()));
        }
    }

    /**
     * Extracts the current user ID from the security context.
     * @return The user ID
     * @throws ResourceNotFoundException if user is not authenticated
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResourceNotFoundException("User not authenticated");
        }
        
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        
        return user.getId();
    }

    /**
     * Verifies that the user has the STUDENT role.
     * @param userId The user ID
     * @throws AccessDeniedException if user doesn't have STUDENT role
     */
    private void verifyStudentRole(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        if (user.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("This endpoint is only accessible to students");
        }
    }
}

