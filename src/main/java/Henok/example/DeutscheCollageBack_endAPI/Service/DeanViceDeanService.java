package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.AssessmentScoresResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.DeanAndVice_Dean.*;
import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.DeanViceDeanRegisterRequest;
import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.UserRegisterRequest;
import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Region;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Woreda;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Zone;
import Henok.example.DeutscheCollageBack_endAPI.Enums.AssessmentStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

// DeanViceDeanService
// Handles registration logic for Dean and Vice-Dean.
// Keeps business logic separate from controller.
// Uses transactional behavior to ensure atomicity when creating User + DeanViceDeanDetails.
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class DeanViceDeanService {

    private final UserService userService; // existing service for user registration
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final DeanViceDeanDetailsRepository deanViceDeanDetailsRepository;
    private final StudentDetailsRepository studentDetailsRepository;
    private final AssessmentRepo assessmentRepository;
    private final StudentCourseScoreRepo studentCourseScoreRepo;
    private final StudentAssessmentRepo studentAssessmentRepository;
    private final TeacherCourseAssignmentRepository assignmentRepository;
    private final DepartmentRepo departmentRepository;
    private final ProgramModalityRepository programModalityRepository;
    private final ProgramLevelRepository programLevelRepository;
    private final WoredaRepository woredaRepository;
    private final ZoneRepository zoneRepository;
    private final RegionRepository regionRepository;
    private final PasswordEncoder passwordEncoder; // injected via Spring Security config

    // Common registration logic used by both Dean and Vice-Dean controllers
    // Why: Avoid code duplication while allowing different roles to be set externally.
    public Long registerDeanViceDean(DeanViceDeanRegisterRequest request, Role role,
                                     MultipartFile photograph, MultipartFile document) {

        // Step 1: Register the User with the specified role (DEAN or VICE_DEAN)
        UserRegisterRequest userReq = new UserRegisterRequest();
        userReq.setUsername(request.getUsername());
        userReq.setPassword(request.getPassword());
        userReq.setRole(role);

        User savedUser = userService.registerUser(userReq);

        // Step 2: Fetch location entities (with validation)
        Woreda woreda = woredaRepository.findById(request.getResidenceWoredaCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid residence Woreda code"));

        Zone zone = zoneRepository.findById(request.getResidenceZoneCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid residence Zone code"));

        Region region = regionRepository.findById(request.getResidenceRegionCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid residence Region code"));

        // Step 3: Convert MultipartFile to byte[] (handle nulls gracefully)
        byte[] photoBytes = null;
        if (photograph != null && !photograph.isEmpty()) {
            try {
                photoBytes = photograph.getBytes();
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to process photograph");
            }
        }

        byte[] documentBytes = null;
        if (document != null && !document.isEmpty()) {
            try {
                documentBytes = document.getBytes();
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to process document");
            }
        }

        // Step 4: Build and save DeanViceDeanDetails
        DeanViceDeanDetails details = DeanViceDeanDetails.builder()
                .user(savedUser)
                .firstNameAMH(request.getFirstNameAMH())
                .firstNameENG(request.getFirstNameENG())
                .fatherNameAMH(request.getFatherNameAMH())
                .fatherNameENG(request.getFatherNameENG())
                .grandfatherNameAMH(request.getGrandfatherNameAMH())
                .grandfatherNameENG(request.getGrandfatherNameENG())
                .gender(request.getGender())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .residenceWoreda(woreda)
                .residenceZone(zone)
                .residenceRegion(region)
                .hiredDateGC(request.getHiredDateGC())
                .title(request.getTitle())
                .remarks(request.getRemarks())
                .photo(photoBytes)
                .documents(documentBytes)
                .active(true)
                .build();

        DeanViceDeanDetails saved = deanViceDeanDetailsRepository.save(details);

        List<Role> targetRoles = Arrays.asList(
                Role.DEPARTMENT_HEAD,
                Role.REGISTRAR,
                Role.FINANCIAL_STAFF,
                Role.VICE_DEAN,
                Role.DEAN,
                Role.GENERAL_MANAGER
        );

        String message = saved.getUser().getRole().toString();
        if(saved.getUser().getRole() == Role.VICE_DEAN){
            message = "A new Vice-Dean has been registered: "
                    + saved.getFirstNameENG() + " "
                    + saved.getFatherNameENG() + " "
                    + saved.getGrandfatherNameENG();
        } else if (saved.getUser().getRole() == Role.DEAN) {
            message = "A new Vice-Dean has been registered: "
                    + saved.getFirstNameENG() + " "
                    + saved.getFatherNameENG() + " "
                    + saved.getGrandfatherNameENG();
        }
        notificationService.createNotification(
                targetRoles,    // roles to notify
                null,           // no single user
                Role.DEPARTMENT_HEAD,  // sender role (or Role.REGISTRAR if admin does this)
                message
        );

        return saved.getId(); // return the details ID for confirmation
    }


    // Fetch all active by role
    // Why: Retrieves list of active DeanViceDeanDetails filtered by User role.
    // Maps to DTO, computes hasDocument, fetches location names.
    @Transactional(readOnly = true)
    public List<DeanViceDeanListDTO> getAllActiveByRole(Role role) {
        List<DeanViceDeanDetails> detailsList = deanViceDeanDetailsRepository.findByUserRoleAndActiveTrue(role);
        return detailsList.stream().map(this::mapToListDTO).collect(Collectors.toList());
    }

    // Add these methods to DeanViceDeanService

    // Retrieve detailed information for a Dean/Vice-Dean by details ID.
// Throws exception if not found or role mismatch.
// Maps entity to DTO, computes hasPhoto/hasDocument flags.
    @Transactional(readOnly = true)
    public DeanViceDeanDetailDTO getDetailById(Long id, Role expectedRole) {
        DeanViceDeanDetails details = deanViceDeanDetailsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dean/Vice-Dean not found with ID: " + id));

        if (details.getUser().getRole() != expectedRole) {
            throw new IllegalArgumentException("Role mismatch: expected " + expectedRole);
        }

        if (!details.isActive()) {
            throw new IllegalArgumentException("This Dean/Vice-Dean is no longer active");
        }

        DeanViceDeanDetailDTO dto = new DeanViceDeanDetailDTO();
        dto.setId(details.getId());
        dto.setUserId(details.getUser().getId());
        dto.setUsername(details.getUser().getUsername());
        dto.setFirstNameAMH(details.getFirstNameAMH());
        dto.setFirstNameENG(details.getFirstNameENG());
        dto.setFatherNameAMH(details.getFatherNameAMH());
        dto.setFatherNameENG(details.getFatherNameENG());
        dto.setGrandfatherNameAMH(details.getGrandfatherNameAMH());
        dto.setGrandfatherNameENG(details.getGrandfatherNameENG());
        dto.setGender(details.getGender());
        dto.setEmail(details.getEmail());
        dto.setPhoneNumber(details.getPhoneNumber());

        // Residence details
        dto.setResidenceRegion(details.getResidenceRegion().getRegion());
        dto.setResidenceRegionCode(details.getResidenceRegion().getRegionCode());
        dto.setResidenceZone(details.getResidenceZone().getZone());
        dto.setResidenceZoneCode(details.getResidenceZone().getZoneCode());
        dto.setResidenceWoreda(details.getResidenceWoreda().getWoreda());
        dto.setResidenceWoredaCode(details.getResidenceWoreda().getWoredaCode());

        dto.setHiredDateGC(details.getHiredDateGC());
        dto.setTitle(details.getTitle());
        dto.setRemarks(details.getRemarks());
        dto.setHasPhoto(details.getPhoto() != null && details.getPhoto().length > 0);
        dto.setHasDocument(details.getDocuments() != null && details.getDocuments().length > 0);
        dto.setRole(details.getUser().getRole());
        dto.setActive(details.isActive());

        return dto;
    }

    // Helper mapper for ListDTO
    private DeanViceDeanListDTO mapToListDTO(DeanViceDeanDetails details) {
        DeanViceDeanListDTO dto = new DeanViceDeanListDTO();
        dto.setId(details.getId());
        dto.setUserId(details.getUser().getId());
        dto.setUsername(details.getUser().getUsername());
        dto.setFirstNameAMH(details.getFirstNameAMH());
        dto.setFirstNameENG(details.getFirstNameENG());
        dto.setFatherNameAMH(details.getFatherNameAMH());
        dto.setFatherNameENG(details.getFatherNameENG());
        dto.setGrandfatherNameAMH(details.getGrandfatherNameAMH());
        dto.setGrandfatherNameENG(details.getGrandfatherNameENG());
        dto.setGender(details.getGender());
        dto.setEmail(details.getEmail());
        dto.setPhoneNumber(details.getPhoneNumber());
        dto.setResidenceRegion(details.getResidenceRegion().getRegion());
        dto.setResidenceZone(details.getResidenceZone().getZone());
        dto.setResidenceWoreda(details.getResidenceWoreda().getWoreda());
        dto.setHiredDateGC(details.getHiredDateGC());
        dto.setTitle(details.getTitle());
        dto.setRemarks(details.getRemarks());
        dto.setHasDocument(details.getDocuments() != null && details.getDocuments().length > 0);
        dto.setPhoto(details.getPhoto());
        dto.setRole(details.getUser().getRole());
        return dto;
    }

    // Update by ID
    // Why: Partial update - only apply non-null/non-empty fields.
    // Cannot update username or documents (as per requirement).
    // Handles photo update separately.
    // Validates and updates locations if codes provided.
    public void updateDeanViceDean(Long id, DeanViceDeanUpdateRequest request, MultipartFile photo, MultipartFile document, Role expectedRole) {
        DeanViceDeanDetails details = deanViceDeanDetailsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dean/Vice-Dean not found with ID: " + id));

        if (details.getUser().getRole() != expectedRole) {
            throw new IllegalArgumentException("Role mismatch for update");
        }

        User user = details.getUser();

        // Update simple fields if not empty/null
        if (request.getFirstNameAMH() != null && !request.getFirstNameAMH().isEmpty()) {
            details.setFirstNameAMH(request.getFirstNameAMH());
        }
        if (request.getFirstNameENG() != null && !request.getFirstNameENG().isEmpty()) {
            details.setFirstNameENG(request.getFirstNameENG());
        }
        if (request.getFatherNameAMH() != null && !request.getFatherNameAMH().isEmpty()) {
            details.setFatherNameAMH(request.getFatherNameAMH());
        }
        if (request.getFatherNameENG() != null && !request.getFatherNameENG().isEmpty()) {
            details.setFatherNameENG(request.getFatherNameENG());
        }
        if (request.getGrandfatherNameAMH() != null && !request.getGrandfatherNameAMH().isEmpty()) {
            details.setGrandfatherNameAMH(request.getGrandfatherNameAMH());
        }
        if (request.getGrandfatherNameENG() != null && !request.getGrandfatherNameENG().isEmpty()) {
            details.setGrandfatherNameENG(request.getGrandfatherNameENG());
        }
        if (request.getGender() != null) {
            details.setGender(request.getGender());
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            details.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isEmpty()) {
            if (deanViceDeanDetailsRepository.existsByPhoneNumberAndIdNot(request.getPhoneNumber(), id)) {
                throw new IllegalArgumentException("Phone number already in use");
            }
            details.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getHiredDateGC() != null) {
            details.setHiredDateGC(request.getHiredDateGC());
        }
        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            details.setTitle(request.getTitle());
        }
        if (request.getRemarks() != null && !request.getRemarks().isEmpty()) {
            details.setRemarks(request.getRemarks());
        }

        // Update locations if codes provided
        if (request.getResidenceWoredaCode() != null && !request.getResidenceWoredaCode().isEmpty()) {
            Woreda woreda = woredaRepository.findById(request.getResidenceWoredaCode())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid residence Woreda code"));
            details.setResidenceWoreda(woreda);
        }
        if (request.getResidenceZoneCode() != null && !request.getResidenceZoneCode().isEmpty()) {
            Zone zone = zoneRepository.findById(request.getResidenceZoneCode())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid residence Zone code"));
            details.setResidenceZone(zone);
        }
        if (request.getResidenceRegionCode() != null && !request.getResidenceRegionCode().isEmpty()) {
            Region region = regionRepository.findById(request.getResidenceRegionCode())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid residence Region code"));
            details.setResidenceRegion(region);
        }

        // Update photo if provided
        if (photo != null && !photo.isEmpty()) {
            try {
                details.setPhoto(photo.getBytes());
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to process photograph");
            }
        }

        if (document != null && !document.isEmpty()) {
            try {
                details.setDocuments(document.getBytes());
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to process document file");
            }
        }

        // Save updated user and details
        userRepository.save(user);
        deanViceDeanDetailsRepository.save(details);
    }
    
    // Self-update method
    // - Based on Authenticated User
    // - Restricted: Documents, HiredDate, UserInfo
    // - Allowed: Names, Gender, Phone, Email, Title, Remarks, Address, Photo
    public void updateSelf(User user, DeanViceDeanUpdateRequest request, MultipartFile photo) {
        DeanViceDeanDetails details = deanViceDeanDetailsRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found for user"));

        // Update simple fields if not empty/null
        if (request.getFirstNameAMH() != null && !request.getFirstNameAMH().isEmpty()) {
            details.setFirstNameAMH(request.getFirstNameAMH());
        }
        if (request.getFirstNameENG() != null && !request.getFirstNameENG().isEmpty()) {
            details.setFirstNameENG(request.getFirstNameENG());
        }
        if (request.getFatherNameAMH() != null && !request.getFatherNameAMH().isEmpty()) {
            details.setFatherNameAMH(request.getFatherNameAMH());
        }
        if (request.getFatherNameENG() != null && !request.getFatherNameENG().isEmpty()) {
            details.setFatherNameENG(request.getFatherNameENG());
        }
        if (request.getGrandfatherNameAMH() != null && !request.getGrandfatherNameAMH().isEmpty()) {
            details.setGrandfatherNameAMH(request.getGrandfatherNameAMH());
        }
        if (request.getGrandfatherNameENG() != null && !request.getGrandfatherNameENG().isEmpty()) {
            details.setGrandfatherNameENG(request.getGrandfatherNameENG());
        }
        if (request.getGender() != null) {
            details.setGender(request.getGender());
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            details.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isEmpty()) {
            // Check uniqueness if changed
            if (!request.getPhoneNumber().equals(details.getPhoneNumber())) {
                if (deanViceDeanDetailsRepository.existsByPhoneNumberAndIdNot(request.getPhoneNumber(), details.getId())) {
                    throw new IllegalArgumentException("Phone number already in use");
                }
                details.setPhoneNumber(request.getPhoneNumber());
            }
        }
        
        // HiredDate IS RESTRICTED
        
        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            details.setTitle(request.getTitle());
        }
        if (request.getRemarks() != null && !request.getRemarks().isEmpty()) {
            details.setRemarks(request.getRemarks());
        }

        // Update locations if codes provided
        if (request.getResidenceWoredaCode() != null && !request.getResidenceWoredaCode().isEmpty()) {
            Woreda woreda = woredaRepository.findById(request.getResidenceWoredaCode())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid residence Woreda code"));
            details.setResidenceWoreda(woreda);
        }
        if (request.getResidenceZoneCode() != null && !request.getResidenceZoneCode().isEmpty()) {
            Zone zone = zoneRepository.findById(request.getResidenceZoneCode())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid residence Zone code"));
            details.setResidenceZone(zone);
        }
        if (request.getResidenceRegionCode() != null && !request.getResidenceRegionCode().isEmpty()) {
            Region region = regionRepository.findById(request.getResidenceRegionCode())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid residence Region code"));
            details.setResidenceRegion(region);
        }

        // Update photo if provided 
        // Documents IS RESTRICTED (so we don't accept 'document' param here)
        if (photo != null && !photo.isEmpty()) {
            try {
                details.setPhoto(photo.getBytes());
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to process photograph");
            }
        }

        // Save updated user and details
        deanViceDeanDetailsRepository.save(details);
    }

    public ResponseEntity<?> getDocumentByIdAndRole(Long id, Role expectedRole) {
        try {
            DeanViceDeanDetails details = deanViceDeanDetailsRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Dean/Vice-Dean not found with ID: " + id));

            if (details.getUser().getRole() != expectedRole) {
                throw new IllegalArgumentException("Role mismatch");
            }

            byte[] document = details.getDocuments();
            if (document == null || document.length == 0) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Document not found for ID: " + id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            // Optional: set proper content type if you store filename/mime, here default octet-stream
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document_" + id + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(document);

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

    // Get profile for authenticated user
    // Why: Fetches details for the given user, maps to ProfileDTO without remarks.
    @Transactional(readOnly = true)
    public DeanViceDeanProfileDTO getProfile(User user) {
        DeanViceDeanDetails details = deanViceDeanDetailsRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found for user"));

        return mapToProfileDTO(details);
    }

    // Helper mapper for ProfileDTO
    private DeanViceDeanProfileDTO mapToProfileDTO(DeanViceDeanDetails details) {
        DeanViceDeanProfileDTO dto = new DeanViceDeanProfileDTO();
        dto.setId(details.getId());
        dto.setUsername(details.getUser().getUsername());
        dto.setFirstNameAMH(details.getFirstNameAMH());
        dto.setFirstNameENG(details.getFirstNameENG());
        dto.setFatherNameAMH(details.getFatherNameAMH());
        dto.setFatherNameENG(details.getFatherNameENG());
        dto.setGrandfatherNameAMH(details.getGrandfatherNameAMH());
        dto.setGrandfatherNameENG(details.getGrandfatherNameENG());
        dto.setGender(details.getGender());
        dto.setEmail(details.getEmail());
        dto.setPhoneNumber(details.getPhoneNumber());
        dto.setResidenceRegion(details.getResidenceRegion().getRegion());
        dto.setResidenceRegionCode(details.getResidenceRegion().getRegionCode());
        dto.setResidenceZone(details.getResidenceZone().getZone());
        dto.setResidenceZoneCode(details.getResidenceZone().getZoneCode());
        dto.setResidenceWoreda(details.getResidenceWoreda().getWoreda());
        dto.setResidenceWoredaCode(details.getResidenceWoreda().getWoredaCode());
        dto.setHiredDateGC(details.getHiredDateGC());
        dto.setTitle(details.getTitle());
        dto.setPhoto(details.getPhoto());
        dto.setRole(details.getUser().getRole());
        // Remarks excluded as per requirement
        return dto;
    }


    // Helper to retrieve User from saved DeanViceDeanDetails (needed for userId in response)
    // Why: We only return detailsId from registration, but response needs User.id
    @Transactional(readOnly = true)
    public User getUserByDetailsId(Long detailsId) {
        return deanViceDeanDetailsRepository.findById(detailsId)
                .orElseThrow(() -> new IllegalStateException("Dean/Vice-Dean details not found"))
                .getUser();
    }


    // Fetch dashboard data for Dean.
    // Why: Aggregates multiple queries into a single method for efficiency.
    // Uses custom repository queries for groupings/counts to avoid loading all entities.
    @Transactional(readOnly = true)
    public DeanDashboardDTO getDashboardData() {
        DeanDashboardDTO dto = new DeanDashboardDTO();

        // Total students: Simple count from StudentDetails.
        dto.setTotalStudents(studentDetailsRepository.count());

        // Total departments: Count from Department.
        dto.setTotalDepartments(departmentRepository.count());

        // Total department heads: Count users with ROLE_DEPARTMENT_HEAD.
        dto.setTotalDepartmentHeads(userRepository.countByRole(Role.DEPARTMENT_HEAD));

        // Active modalities: Count all ProgramModality (no active flag, assume all active).
        // If needed, filter if an active field is added later.
        dto.setActiveModalities(programModalityRepository.count());

        // Active levels: Count ProgramLevel where active = true.
        dto.setActiveLevels(programLevelRepository.countByActiveTrue());

        // Students by level: Group StudentDetails by programModality.programLevel.name.
        // Use custom query to get Map<levelName, count>.
        dto.setStudentsByLevel(studentDetailsRepository.countStudentsByProgramLevel());

        // Students by modality: Group by programModality.modality.
        dto.setStudentsByModality(studentDetailsRepository.countStudentsByModality());

        // Students per department: Group by departmentEnrolled.deptName.
        dto.setStudentsPerDepartment(studentDetailsRepository.countStudentsPerDepartment());

        // Enrollment trend: Group by academicYear.yearCode (assuming AcademicYear has yearCode).
        // If academicYear is null for some, fallback to year from dateEnrolledGC.
        dto.setEnrollmentTrend(studentDetailsRepository.getEnrollmentTrendByAcademicYear());

        // Gender distribution: Group by gender.
        dto.setGenderDistribution(studentDetailsRepository.countByGender());

        // Additional: Total teachers.
        dto.setTotalTeachers(userRepository.countByRole(Role.TEACHER));

        // Average Grade 12 result: Average of grade12Result (ignore nulls).
        dto.setAverageGrade12Result(studentDetailsRepository.getAverageGrade12Result().orElse(0.0));

        // Exit exam pass rate: (count where isStudentPassExitExam = true) / totalStudents * 100.
        long passCount = studentDetailsRepository.countByIsStudentPassExitExamTrue();
//        dto.setExitExamPassRate(dto.getTotalStudents > 0 ? (double) passCount / dto.getTotalStudents * 100 : 0.0);

        return dto;
    }

    @Transactional(readOnly = true)
    public byte[] getDeanViceDeanPhoto(Long id, Role expectedRole) {
        DeanViceDeanDetails details = deanViceDeanDetailsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dean/Vice-Dean not found with ID: " + id));
        if (details.getUser().getRole() != expectedRole) {
            throw new IllegalArgumentException("Role mismatch");
        }
        return details.getPhoto();
    }

    @Transactional(readOnly = true)
    public byte[] getDeanViceDeanDocument(Long id, Role expectedRole) {
        DeanViceDeanDetails details = deanViceDeanDetailsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dean/Vice-Dean not found with ID: " + id));
        if (details.getUser().getRole() != expectedRole) {
            throw new IllegalArgumentException("Role mismatch");
        }
        return details.getDocuments();
    }

    /**
     * Retrieves scores grid for all assessments that have been APPROVED by department heads
     * (headApproval = ACCEPTED) across the entire university.
     *
     * Why this method for deans:
     * - Deans have university-wide access → no department filter
     * - Filters only head-approved assessments for dean's review
     * - Same DTO as registrar's view for consistency
     *
     * @param authenticatedUser The authenticated dean
     * @return List of AssessmentScoresResponse (one per teacher course assignment)
     */
    @Transactional(readOnly = true)
    public List<AssessmentScoresResponse> getHeadApprovedAssessmentsForDean(User authenticatedUser) {

        // Verify dean profile (assuming DeanDetails exists)
        DeanViceDeanDetails dean = deanViceDeanDetailsRepository.findByUser(authenticatedUser)
                .orElseThrow(() -> new ResourceNotFoundException("Dean profile not found"));

        // Get ALL assessments where headApproval = ACCEPTED (university-wide)
        List<Assessment> headApprovedAssessments = assessmentRepository.findAll().stream()
                .filter(a -> a.getHeadApproval() == AssessmentStatus.ACCEPTED)
                .collect(Collectors.toList());

        if (headApprovedAssessments.isEmpty()) {
            return Collections.emptyList(); // Graceful empty response
        }

        // Group by teacher course assignment
        Map<TeacherCourseAssignment, List<Assessment>> byAssignment = headApprovedAssessments.stream()
                .collect(Collectors.groupingBy(Assessment::getTeacherCourseAssignment));

        // Build responses for each assignment
        List<AssessmentScoresResponse> responses = new ArrayList<>();
        for (Map.Entry<TeacherCourseAssignment, List<Assessment>> entry : byAssignment.entrySet()) {
            TeacherCourseAssignment tca = entry.getKey();
            List<Assessment> assessments = entry.getValue();

            AssessmentScoresResponse response = new AssessmentScoresResponse();
            response.setTeacherCourseAssignmentId(tca.getId());
            response.setTeacherName(tca.getTeacher().getFirstNameEnglish() + " " + tca.getTeacher().getLastNameEnglish());
            response.setCourseCode(tca.getCourse().getCCode());
            response.setCourseTitle(tca.getCourse().getCTitle());
            response.setBatchClassYearSemester(tca.getBcys().getDisplayName());

            // Build assessment infos – include deanApproval for dean's view
            List<AssessmentScoresResponse.AssessmentInfo> assessmentInfos = assessments.stream()
                    .sorted((a1, a2) -> a1.getCreatedAt().compareTo(a2.getCreatedAt()))
                    .map(a -> {
                        AssessmentScoresResponse.AssessmentInfo info = new AssessmentScoresResponse.AssessmentInfo();
                        info.setAssessmentId(a.getAssID());
                        info.setTitle(a.getAssTitle());
                        info.setMaxScore(a.getMaxScore());
                        info.setDueDate(a.getDueDate());
                        info.setStatus(a.getAssStatus());
                        info.setHeadApproval(a.getHeadApproval());
                        info.setDeanApproval(a.getDeanApproval()); // New: show dean's own approval status
                        info.setRegistrarApproval(a.getRegistrarApproval());
                        return info;
                    })
                    .collect(Collectors.toList());
            response.setAssessments(assessmentInfos);

            // Get enrolled students for this course + BCYS
            List<StudentCourseScore> courseScores = studentCourseScoreRepo.findByCourseAndBatchClassYearSemester(
                    tca.getCourse(), tca.getBcys());

            Set<User> uniqueUsers = courseScores.stream()
                    .map(StudentCourseScore::getStudent)
                    .collect(Collectors.toSet());

            // Build student score views
            List<AssessmentScoresResponse.StudentScoreView> studentViews = uniqueUsers.stream()
                    .map(user -> {
                        StudentDetails student = studentDetailsRepository.findByUser(user).orElse(null);
                        if (student == null) return null;

                        AssessmentScoresResponse.StudentScoreView view = new AssessmentScoresResponse.StudentScoreView();
                        view.setStudentId(student.getId());
                        view.setStudentIdNumber(user.getUsername());
                        view.setFullNameENG(student.getFirstNameENG() + " " + student.getFatherNameENG() + " " + student.getGrandfatherNameENG());
                        view.setFullNameAMH(student.getFirstNameAMH() + " " + student.getFatherNameAMH() + " " + student.getGrandfatherNameAMH());

                        // Scores for each assessment
                        List<AssessmentScoresResponse.SingleScore> scores = assessments.stream()
                                .map(ass -> {
                                    StudentAssessmentKey key = new StudentAssessmentKey(student.getId(), ass.getAssID());
                                    Double score = studentAssessmentRepository.findById(key)
                                            .map(StudentAssessment::getScore)
                                            .orElse(null);
                                    return new AssessmentScoresResponse.SingleScore(ass.getAssID(), score);
                                })
                                .collect(Collectors.toList());
                        view.setScores(scores);
                        return view;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            response.setStudents(studentViews);
            responses.add(response);
        }

        return responses;
    }

    /**
     * Allows dean to approve or reject all assessments in a teacher course assignment.
     *
     * Approval rules:
     * 1. All assessments must already be APPROVED by department head (headApproval = ACCEPTED)
     * 2. Registrar must NOT have accepted yet (registrarApproval must be PENDING or REJECTED)
     * 3. If dean REJECTS → sets deanApproval = REJECTED and reverts headApproval back to PENDING
     * 4. If dean APPROVES → sets deanApproval = ACCEPTED and notifies registrars
     *
     * @param authenticatedUser The authenticated dean
     * @param teacherCourseAssignmentId The assignment to process
     * @param status ACCEPTED or REJECTED
     * @return List of updated assessments
     * @throws IllegalArgumentException if rules are violated
     */
    @Transactional
    public List<Assessment> approveOrRejectAllAssessmentsInAssignment(
            User authenticatedUser,
            Long teacherCourseAssignmentId,
            AssessmentStatus status) {

        // Validate input status
        if (status != AssessmentStatus.ACCEPTED && status != AssessmentStatus.REJECTED) {
            throw new IllegalArgumentException("Status must be ACCEPTED or REJECTED");
        }

        // Verify dean profile
        DeanViceDeanDetails dean = deanViceDeanDetailsRepository.findByUser(authenticatedUser)
                .orElseThrow(() -> new ResourceNotFoundException("Dean profile not found"));

        // Load the assignment
        TeacherCourseAssignment tca = assignmentRepository.findById(teacherCourseAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher course assignment not found"));

        // Get all assessments in this assignment
        List<Assessment> assessments = assessmentRepository.findByTeacherCourseAssignment(tca);

        if (assessments.isEmpty()) {
            throw new ResourceNotFoundException("No assessments found for this course assignment");
        }

        // Rule 1: All must be head-approved
        for (Assessment a : assessments) {
            if (a.getHeadApproval() != AssessmentStatus.ACCEPTED) {
                throw new IllegalArgumentException(
                        "All assessments must be approved by department head first. " +
                                "Assessment '" + a.getAssTitle() + "' has headApproval = " + a.getHeadApproval()
                );
            }
        }

        // Rule 2: Registrar must not have accepted yet
        for (Assessment a : assessments) {
            if (a.getRegistrarApproval() == AssessmentStatus.ACCEPTED) {
                throw new IllegalArgumentException(
                        "Cannot modify – registrar has already accepted assessment '" + a.getAssTitle() + "'"
                );
            }
        }

        List<Assessment> updatedAssessments = new ArrayList<>();

        for (Assessment assessment : assessments) {
            // Apply dean's decision
            assessment.setDeanApproval(status);

            // If dean REJECTS → revert head approval to PENDING
            if (status == AssessmentStatus.REJECTED) {
                assessment.setHeadApproval(AssessmentStatus.PENDING);
            }
            // If dean APPROVES → revert registrar approval to PENDING
            if (status == AssessmentStatus.ACCEPTED) {
                assessment.setRegistrarApproval(AssessmentStatus.PENDING);
            }

            updatedAssessments.add(assessmentRepository.save(assessment));
        }

        // If dean APPROVES → notify registrars
        if (status == AssessmentStatus.ACCEPTED) {
            String message = createBulkNotificationMessage(tca.getCourse(), tca, assessments.size());

            notificationService.createNotification(
                    List.of(Role.REGISTRAR),
                    null,
                    Role.DEAN,
                    message
            );
        }

        return updatedAssessments;
    }

    // Helper for notification message
    private String createBulkNotificationMessage(Course course, TeacherCourseAssignment tca, int count) {
        String courseCode = course.getCCode();
        String courseTitle = course.getCTitle();
        String teacherName = tca.getTeacher().getFirstNameEnglish() + " " + tca.getTeacher().getLastNameEnglish();

        return String.format(
                "Dean has approved all %d assessments for course %s (%s) taught by %s. Please review for final release.",
                count,
                courseCode,
                courseTitle,
                teacherName
        );
    }
}