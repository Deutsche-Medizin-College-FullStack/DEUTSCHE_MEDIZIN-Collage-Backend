package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.AssessmentScoresResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.DepartmentHead.DepartmentHeadDashboardDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.DepartmentHead.DepartmentTeacherDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Heads.DepartmentHeadResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Heads.DepartmentHeadUpdateRequest;
import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.DepartmentHeadRegistrationRequest;
import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Region;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Woreda;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Zone;
import Henok.example.DeutscheCollageBack_endAPI.Enums.AssessmentStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Gender;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Error.BadRequestException;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.RegionRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.WoredaRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class DepartmentHeadService {

    private final UserRepository userRepository;
    private final DepartmentHeadRepository departmentHeadRepository;
    private final DepartmentRepo departmentRepository;
    private final RegionRepository regionRepository;
    private final ZoneRepository zoneRepository;
    private final WoredaRepository woredaRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final StudentDetailsRepository studentDetailsRepository;
    private final CourseRepo courseRepo;
    private final TeacherCourseAssignmentRepository teacherCourseAssignmentRepository;
    private final TeacherRepository teacherRepository;
    private final AssessmentRepo assessmentRepository;
    private final StudentAssessmentRepo studentAssessmentRepository;
    private final StudentCourseScoreRepo studentCourseScoreRepo;


    public Map<String, Object> registerDepartmentHead(DepartmentHeadRegistrationRequest req) {

        // 1. Password match
        if (!req.getPassword().equals(req.getPasswordConfirm())) {
            throw new BadRequestException("Password and confirmation do not match");
        }

        // 2. Username uniqueness
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BadRequestException("Username already taken");
        }

        // 3. Load Department
        Department department = departmentRepository.findById(req.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + req.getDepartmentId()));

        // NEW: Check if this department already has a head
        if (departmentHeadRepository.existsByDepartment(department)) {
            throw new BadRequestException("This department already has an assigned department head");
        }

        // 4. Load and validate residence hierarchy
        Region region = regionRepository.findById(req.getResidenceRegionCode())
                .orElseThrow(() -> new BadRequestException("Invalid residence region code"));

        Zone zone = zoneRepository.findById(req.getResidenceZoneCode())
                .orElseThrow(() -> new BadRequestException("Invalid residence zone code"));
        if (!zone.getRegion().getRegionCode().equals(region.getRegionCode())) {
            throw new BadRequestException("Zone does not belong to the selected region");
        }

        Woreda woreda = woredaRepository.findById(req.getResidenceWoredaCode())
                .orElseThrow(() -> new BadRequestException("Invalid residence woreda code"));
        if (!woreda.getZone().getZoneCode().equals(zone.getZoneCode())) {
            throw new BadRequestException("Woreda does not belong to the selected zone");
        }

        // 5. File size validation
        if (req.getPhoto() != null && !req.getPhoto().isEmpty())
            validateFileSize(req.getPhoto(), 2 * 1024 * 1024, "Photo");
        if (req.getDocuments() != null && !req.getDocuments().isEmpty())
            validateFileSize(req.getDocuments(), 5 * 1024 * 1024, "Documents");

        // 6. Create and save User
        User user = User.builder()
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(Role.DEPARTMENT_HEAD)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .enabled(true)
                .build();

        user = userRepository.save(user);  // Now we have user ID

        // 7. Create DepartmentHeadDetails
        DepartmentHeadDetails details = new DepartmentHeadDetails();
        details.setUser(user);
        details.setFirstNameENG(req.getFirstNameENG());
        details.setFirstNameAMH(req.getFirstNameAMH());
        details.setFatherNameENG(req.getFatherNameENG());
        details.setFatherNameAMH(req.getFatherNameAMH());
        details.setGrandfatherNameENG(req.getGrandfatherNameENG());
        details.setGrandfatherNameAMH(req.getGrandfatherNameAMH());
        details.setGender(req.getGender());
        details.setPhoneNumber(req.getPhoneNumber());
        details.setEmail(req.getEmail());
        details.setHiredDateGC(req.getHiredDateGC());
        details.setHiredDateEC(req.getHiredDateEC());
        details.setDepartment(department);
        details.setResidenceRegion(region);
        details.setResidenceZone(zone);
        details.setResidenceWoreda(woreda);
        details.setRemark(req.getRemark());
        details.setActive(true);

        // Handle files
        if (req.getPhoto() != null && !req.getPhoto().isEmpty()) {
            try {
                details.setPhoto(req.getPhoto().getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to process photo");
            }
        }
        if (req.getDocuments() != null && !req.getDocuments().isEmpty()) {
            try {
                details.setDocuments(req.getDocuments().getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to process documents");
            }
        }

        departmentHeadRepository.save(details);

        // Send notification
        // Create notification for all users except STUDENTS and TEACHERS
        List<Role> targetRoles = Arrays.asList(
                Role.DEPARTMENT_HEAD,
                Role.REGISTRAR,
                Role.FINANCIAL_STAFF,
                Role.VICE_DEAN,
                Role.DEAN,
                Role.GENERAL_MANAGER
        );

        String message = "A new Department Head has been registered: "
                + req.getFirstNameENG() + " " + req.getFatherNameENG() + " " + req.getGrandfatherNameENG()
                + " for department " + department.getDeptName();

        notificationService.createNotification(
                targetRoles,    // roles to notify
                null,           // no single user
                Role.DEPARTMENT_HEAD,  // sender role (or Role.REGISTRAR if admin does this)
                message
        );

        // 8. Return only the minimal information required
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Department head registered successfully");
        response.put("userId", user.getId());
        response.put("username", user.getUsername());

        return response;
    }

    // Partial update - only applies fields that are not null/empty
    public DepartmentHeadResponse updateDepartmentHead(Long id, DepartmentHeadUpdateRequest req) {
        DepartmentHeadDetails details = departmentHeadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department head not found with id: " + id));

        // Update textual/personal fields only if provided (not null and not empty for strings)
        if (isNotBlank(req.getFirstNameENG())) details.setFirstNameENG(req.getFirstNameENG());
        if (isNotBlank(req.getFirstNameAMH())) details.setFirstNameAMH(req.getFirstNameAMH());
        if (isNotBlank(req.getFatherNameENG())) details.setFatherNameENG(req.getFatherNameENG());
        if (isNotBlank(req.getFatherNameAMH())) details.setFatherNameAMH(req.getFatherNameAMH());
        if (isNotBlank(req.getGrandfatherNameENG())) details.setGrandfatherNameENG(req.getGrandfatherNameENG());
        if (isNotBlank(req.getGrandfatherNameAMH())) details.setGrandfatherNameAMH(req.getGrandfatherNameAMH());
        if (req.getGender() != null) details.setGender(req.getGender());
        if (isNotBlank(req.getPhoneNumber())) details.setPhoneNumber(req.getPhoneNumber());
        if (isNotBlank(req.getEmail())) details.setEmail(req.getEmail());
        if (req.getHiredDateGC() != null) details.setHiredDateGC(req.getHiredDateGC());
        if (isNotBlank(req.getHiredDateEC())) details.setHiredDateEC(req.getHiredDateEC());
        if (isNotBlank(req.getRemark())) details.setRemark(req.getRemark());
        if (req.getIsActive() != null) details.setActive(req.getIsActive());

        // Update department if new ID provided
        if (req.getDepartmentId() != null) {
            throw new BadRequestException("Department change is not allowed via this endpoint");
        }

        // Update residence address if all three codes provided
        if (isNotBlank(req.getResidenceRegionCode()) &&
                isNotBlank(req.getResidenceZoneCode()) &&
                isNotBlank(req.getResidenceWoredaCode())) {

            Region region = regionRepository.findById(req.getResidenceRegionCode())
                    .orElseThrow(() -> new BadRequestException("Invalid residence region code"));

            Zone zone = zoneRepository.findById(req.getResidenceZoneCode())
                    .orElseThrow(() -> new BadRequestException("Invalid residence zone code"));
            if (!zone.getRegion().getRegionCode().equals(region.getRegionCode())) {
                throw new BadRequestException("Zone does not belong to selected region");
            }

            Woreda woreda = woredaRepository.findById(req.getResidenceWoredaCode())
                    .orElseThrow(() -> new BadRequestException("Invalid residence woreda code"));
            if (!woreda.getZone().getZoneCode().equals(zone.getZoneCode())) {
                throw new BadRequestException("Woreda does not belong to selected zone");
            }

            details.setResidenceRegion(region);
            details.setResidenceZone(zone);
            details.setResidenceWoreda(woreda);
        }

        // Replace photo if new one uploaded
        if (req.getPhoto() != null && !req.getPhoto().isEmpty()) {
            validateFileSize(req.getPhoto(), 2 * 1024 * 1024, "Photo");
            try {
                details.setPhoto(req.getPhoto().getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to process new photo");
            }
        }

        // Replace documents if new one uploaded
        if (req.getDocuments() != null && !req.getDocuments().isEmpty()) {
            validateFileSize(req.getDocuments(), 5 * 1024 * 1024, "Documents");
            try {
                details.setDocuments(req.getDocuments().getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to process new documents");
            }
        }

        DepartmentHeadDetails saved = departmentHeadRepository.save(details);
        return mapToResponse(saved);
    }

    // New self update partial method using User token source
    public DepartmentHeadResponse updateDepartmentHeadSelf(User user, DepartmentHeadUpdateRequest req, MultipartFile photo) {
        DepartmentHeadDetails details = departmentHeadRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Department head profile not found"));

        if (isNotBlank(req.getFirstNameENG())) details.setFirstNameENG(req.getFirstNameENG());
        if (isNotBlank(req.getFirstNameAMH())) details.setFirstNameAMH(req.getFirstNameAMH());
        if (isNotBlank(req.getFatherNameENG())) details.setFatherNameENG(req.getFatherNameENG());
        if (isNotBlank(req.getFatherNameAMH())) details.setFatherNameAMH(req.getFatherNameAMH());
        if (isNotBlank(req.getGrandfatherNameENG())) details.setGrandfatherNameENG(req.getGrandfatherNameENG());
        if (isNotBlank(req.getGrandfatherNameAMH())) details.setGrandfatherNameAMH(req.getGrandfatherNameAMH());
        if (req.getGender() != null) details.setGender(req.getGender());
        if (isNotBlank(req.getPhoneNumber())) details.setPhoneNumber(req.getPhoneNumber());
        if (isNotBlank(req.getEmail())) details.setEmail(req.getEmail());
        
        // RESTRICTED: HiredDate, Remark, IsActive, DepartmentId
        // The prompt says "department heads cant update... hired date EC or GC, documents, isActive, remark, any user Info"
        // So we intentionally SKIP setHiredDate, setRemark, setActive
        
        // Update residence address if available
        if (isNotBlank(req.getResidenceRegionCode()) &&
                isNotBlank(req.getResidenceZoneCode()) &&
                isNotBlank(req.getResidenceWoredaCode())) {

            Region region = regionRepository.findById(req.getResidenceRegionCode())
                    .orElseThrow(() -> new BadRequestException("Invalid residence region code"));

            Zone zone = zoneRepository.findById(req.getResidenceZoneCode())
                    .orElseThrow(() -> new BadRequestException("Invalid residence zone code"));
            if (!zone.getRegion().getRegionCode().equals(region.getRegionCode())) {
                throw new BadRequestException("Zone does not belong to selected region");
            }

            Woreda woreda = woredaRepository.findById(req.getResidenceWoredaCode())
                    .orElseThrow(() -> new BadRequestException("Invalid residence woreda code"));
            if (!woreda.getZone().getZoneCode().equals(zone.getZoneCode())) {
                throw new BadRequestException("Woreda does not belong to selected zone");
            }

            details.setResidenceRegion(region);
            details.setResidenceZone(zone);
            details.setResidenceWoreda(woreda);
        }

        // Replace photo if provided (Documents restricted)
        if (photo != null && !photo.isEmpty()) {
            validateFileSize(photo, 2 * 1024 * 1024, "Photo");
            try {
                details.setPhoto(photo.getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to process new photo");
            }
        }

        DepartmentHeadDetails saved = departmentHeadRepository.save(details);
        return mapToResponse(saved);
    }

    public List<DepartmentHeadResponse> getAllDepartmentHeads() {
        return departmentHeadRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public DepartmentHeadResponse getDepartmentHeadById(Long id) {
        DepartmentHeadDetails details = departmentHeadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department head not found with id: " + id));
        return mapToResponse(details);
    }

    // Fetches profile for the current department head user.
    // Builds a Map with all details except document/photo blobs.
    // Includes booleans for hasDocument and hasPhoto.
    // Throws ResourceNotFoundException if no profile found for user.
    public Map<String, Object> getMyProfile(User currentUser) {

        DepartmentHeadDetails details = departmentHeadRepository.findByUser(currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for current user"));

        Map<String, Object> response = new HashMap<>();
        response.put("id", details.getId());
        response.put("username", currentUser.getUsername());
        response.put("firstNameENG", details.getFirstNameENG());
        response.put("firstNameAMH", details.getFirstNameAMH());
        response.put("fatherNameENG", details.getFatherNameENG());
        response.put("fatherNameAMH", details.getFatherNameAMH());
        response.put("grandfatherNameENG", details.getGrandfatherNameENG());
        response.put("grandfatherNameAMH", details.getGrandfatherNameAMH());
        response.put("gender", details.getGender());
        response.put("phoneNumber", details.getPhoneNumber());
        response.put("email", details.getEmail());
        response.put("hiredDateGC", details.getHiredDateGC());
        response.put("hiredDateEC", details.getHiredDateEC());
        response.put("remark", details.getRemark());
        response.put("isActive", details.isActive());

        // Booleans for media
        response.put("hasPhoto", details.getPhoto() != null && details.getPhoto().length > 0);
        response.put("hasDocument", details.getDocuments() != null && details.getDocuments().length > 0);

        // Residence places as {id, name}
        response.put("residenceRegion", Map.of(
                "id", details.getResidenceRegion().getRegionCode(),
                "name", details.getResidenceRegion().getRegion()
        ));
        response.put("residenceZone", Map.of(
                "id", details.getResidenceZone().getZoneCode(),
                "name", details.getResidenceZone().getZone()
        ));
        response.put("residenceWoreda", Map.of(
                "id", details.getResidenceWoreda().getWoredaCode(),
                "name", details.getResidenceWoreda().getWoreda()
        ));

        // Department with id, name, modality, level (assuming ProgramModality has getModality(), ProgramLevel has getProgramLevel())
        // If fields differ, adjust accordingly (e.g., getModalityCode() or whatever is the 'name' field)
        Map<String, Object> deptMap = new HashMap<>();
        deptMap.put("id", details.getDepartment().getDptID());
        deptMap.put("name", details.getDepartment().getDeptName());
        if (details.getDepartment().getProgramModality() != null) {
            deptMap.put("modality", details.getDepartment().getProgramModality().getModality());  // Assume getModality() returns string name
        }
        if (details.getDepartment().getProgramLevel() != null) {
            deptMap.put("level", details.getDepartment().getProgramLevel().getName());  // Assume getProgramLevel() returns string name
        }
        response.put("department", deptMap);

        return response;
    }


    // Returns photo bytes for the current user
    public byte[] getMyPhoto(User currentUser) {
        DepartmentHeadDetails details = departmentHeadRepository.findByUser(currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for current user"));

        return details.getPhoto(); // can be null
    }

    // Returns document bytes for the current user
    public byte[] getMyDocument(User currentUser) {
        DepartmentHeadDetails details = departmentHeadRepository.findByUser(currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for current user"));

        return details.getDocuments(); // can be null
    }

    // Helper to map entity → clean response DTO
    private DepartmentHeadResponse mapToResponse(DepartmentHeadDetails details) {
        DepartmentHeadResponse resp = new DepartmentHeadResponse();
        resp.setId(details.getId());
        resp.setUserId(details.getUser().getId());
        resp.setUsername(details.getUser().getUsername());
        resp.setFirstNameENG(details.getFirstNameENG());
        resp.setFirstNameAMH(details.getFirstNameAMH());
        resp.setFatherNameENG(details.getFatherNameENG());
        resp.setFatherNameAMH(details.getFatherNameAMH());
        resp.setGrandfatherNameENG(details.getGrandfatherNameENG());
        resp.setGrandfatherNameAMH(details.getGrandfatherNameAMH());
        resp.setGender(details.getGender());
        resp.setPhoneNumber(details.getPhoneNumber());
        resp.setEmail(details.getEmail());
        resp.setHiredDateGC(details.getHiredDateGC());
        resp.setHiredDateEC(details.getHiredDateEC());
        resp.setRemark(details.getRemark());
        resp.setActive(details.isActive());

        // Department as {id, name}
        resp.setDepartment(Map.of(
                "id", details.getDepartment().getDptID(),
                "name", details.getDepartment().getDeptName()
        ));

        // Residence as simplified objects
        resp.setResidenceRegion(Map.of(
                "id", details.getResidenceRegion().getRegionCode(),
                "name", details.getResidenceRegion().getRegion()
        ));
        resp.setResidenceZone(Map.of(
                "id", details.getResidenceZone().getZoneCode(),
                "name", details.getResidenceZone().getZone()
        ));
        resp.setResidenceWoreda(Map.of(
                "id", details.getResidenceWoreda().getWoredaCode(),
                "name", details.getResidenceWoreda().getWoreda()
        ));

        // Photo detection
        resp.setHasPhoto(details.getPhoto() != null && details.getPhoto().length > 0);
        resp.setHasDocument(details.getDocuments() != null && details.getDocuments().length > 0);

        return resp;
    }

    private boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }

    private void validateFileSize(MultipartFile file, long maxBytes, String fieldName) {
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(fieldName + " size exceeds limit of " + (maxBytes / (1024 * 1024)) + "MB");
        }
    }

    // Why: Separate method so only DEAN role can trigger department change.
    // Enforces uniqueness: no two heads for same department.
    @Transactional
    public Map<String, Object> reassignDepartment(Long headId, Long newDepartmentId) {

        DepartmentHeadDetails head = departmentHeadRepository.findById(headId)
                .orElseThrow(() -> new ResourceNotFoundException("Department head not found with id: " + headId));

        Department newDept = departmentRepository.findById(newDepartmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + newDepartmentId));

        // Check uniqueness: no other head should have this department
        if (departmentHeadRepository.existsByDepartment(newDept)) {
            DepartmentHeadDetails existing = departmentHeadRepository.findByDepartment(newDept)
                    .orElse(null); // optional, just for better message
            String msg = existing != null && existing.getId().equals(headId)
                    ? "Department head is already assigned to this department"
                    : "This department already has an assigned department head";
            throw new BadRequestException(msg);
        }

        // Perform reassignment
        head.setDepartment(newDept);
        departmentHeadRepository.save(head);

        // Build response
        Map<String, Object> deptInfo = new HashMap<>();
        deptInfo.put("id", newDept.getDptID());
        deptInfo.put("name", newDept.getDeptName());
        if (newDept.getProgramModality() != null) {
            deptInfo.put("modality", newDept.getProgramModality().getModality()); // adjust getter if different
        }
        if (newDept.getProgramLevel() != null) {
            deptInfo.put("level", newDept.getProgramLevel().getName()); // adjust getter if different
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Department reassigned successfully");
        response.put("departmentHeadId", head.getId());
        response.put("newDepartment", deptInfo);

        return response;
    }

    /**
     * Gets dashboard information for the authenticated department head.
     * @param authenticatedUser The authenticated department head user
     * @param startDate Start date for new intake/newly hired period (configurable)
     * @param endDate End date for new intake/newly hired period (configurable)
     * @return DepartmentHeadDashboardDTO with all dashboard information
     */
    @Transactional(readOnly = true)
    public DepartmentHeadDashboardDTO getDashboard(User authenticatedUser, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        // Get department head details
        DepartmentHeadDetails head = departmentHeadRepository.findByUser(authenticatedUser)
                .orElseThrow(() -> new ResourceNotFoundException("Department head profile not found"));
        
        Department department = head.getDepartment();
        
        // Build dashboard DTO
        DepartmentHeadDashboardDTO dashboard = new DepartmentHeadDashboardDTO();
        dashboard.setDepartmentHeadName(
                head.getFirstNameENG() + " " +
                head.getFatherNameENG() + " " +
                head.getGrandfatherNameENG()
        );
        
        // 1. Department Info
        DepartmentHeadDashboardDTO.DepartmentInfo deptInfo = new DepartmentHeadDashboardDTO.DepartmentInfo();
        deptInfo.setDepartmentName(department.getDeptName());
        deptInfo.setModality(department.getProgramModality() != null ? department.getProgramModality().getModality() : null);
        deptInfo.setLevel(department.getProgramLevel() != null ? department.getProgramLevel().getName() : null);
        dashboard.setDepartmentInfo(deptInfo);
        
        // 2. Summary
        DepartmentHeadDashboardDTO.Summary summary = new DepartmentHeadDashboardDTO.Summary();
        summary.setTotalStudents((long) studentDetailsRepository.findAll().stream()
                .filter(s -> s.getDepartmentEnrolled() != null && s.getDepartmentEnrolled().getDptID().equals(department.getDptID()))
                .count());
        summary.setTotalCourses((long) courseRepo.findByDepartment(department).size());
        
        // Count unique teachers assigned to courses in this department
        List<Course> departmentCourses = courseRepo.findByDepartment(department);
        Set<Long> teacherIds = new HashSet<>();
        for (Course course : departmentCourses) {
            List<TeacherCourseAssignment> assignments = teacherCourseAssignmentRepository.findAll().stream()
                    .filter(tca -> tca.getCourse().getCID().equals(course.getCID()))
                    .collect(Collectors.toList());
            for (TeacherCourseAssignment tca : assignments) {
                teacherIds.add(tca.getTeacher().getId());
            }
        }
        summary.setTotalTeachers((long) teacherIds.size());
        dashboard.setSummary(summary);
        
        // 3. Pending Approvals (assessments with assStatus = ACCEPTED by teachers)
        List<DepartmentHeadDashboardDTO.PendingApproval> pendingApprovals = new ArrayList<>();
        for (Course course : departmentCourses) {
            List<Assessment> assessments = assessmentRepository.findAll().stream()
                    .filter(a -> a.getTeacherCourseAssignment().getCourse().getCID().equals(course.getCID()))
                    .filter(a -> a.getAssStatus() == AssessmentStatus.ACCEPTED)
                    .collect(Collectors.toList());
            
            if (!assessments.isEmpty()) {
                // Group by teacher
                Map<TeacherDetail, List<Assessment>> byTeacher = assessments.stream()
                        .collect(Collectors.groupingBy(a -> a.getTeacherCourseAssignment().getTeacher()));
                
                for (Map.Entry<TeacherDetail, List<Assessment>> entry : byTeacher.entrySet()) {
                    TeacherDetail teacher = entry.getKey();
                    String fullName = teacher.getFirstNameEnglish() + " " + teacher.getLastNameEnglish();
                    
                    DepartmentHeadDashboardDTO.PendingApproval approval = new DepartmentHeadDashboardDTO.PendingApproval();
                    approval.setTeacher(fullName);
                    approval.setAssessments(entry.getValue().size());
                    approval.setCourse(course.getCTitle());
                    pendingApprovals.add(approval);
                }
            }
        }
        dashboard.setPendingApprovals(pendingApprovals);
        
        // 4. Instructors
        DepartmentHeadDashboardDTO.Instructors instructors = new DepartmentHeadDashboardDTO.Instructors();
        instructors.setTotalTeachers(summary.getTotalTeachers());
        
        // Unassigned teachers (in department but no TeacherCourseAssignment)
        List<TeacherDetail> allDepartmentTeachers = teacherRepository.findAll().stream()
                .filter(t -> t.getDepartment() != null && t.getDepartment().getDptID().equals(department.getDptID()))
                .collect(Collectors.toList());
        
        long unassigned = allDepartmentTeachers.stream()
                .filter(t -> teacherCourseAssignmentRepository.findByTeacher(t).isEmpty())
                .count();
        instructors.setUnassigned(unassigned);
        
        // Newly hired (within period, based on assigned courses)
        long newlyHired = teacherCourseAssignmentRepository.findAll().stream()
                .filter(tca -> departmentCourses.contains(tca.getCourse()))
                .filter(tca -> {
                    TeacherDetail teacher = tca.getTeacher();
                    if (teacher.getHireDateGC() == null) return false;
                    return !teacher.getHireDateGC().isBefore(startDate) && !teacher.getHireDateGC().isAfter(endDate);
                })
                .map(tca -> tca.getTeacher().getId())
                .distinct()
                .count();
        instructors.setNewlyHired(newlyHired);
        dashboard.setInstructors(instructors);
        
        // 5. Students - New Intake
        DepartmentHeadDashboardDTO.Students students = new DepartmentHeadDashboardDTO.Students();
        long newIntake = studentDetailsRepository.findAll().stream()
                .filter(s -> s.getDepartmentEnrolled() != null && s.getDepartmentEnrolled().getDptID().equals(department.getDptID()))
                .filter(s -> {
                    if (s.getDateEnrolledGC() == null) return false;
                    return !s.getDateEnrolledGC().isBefore(startDate) && !s.getDateEnrolledGC().isAfter(endDate);
                })
                .count();
        students.setNewIntake(newIntake);
        dashboard.setStudents(students);
        
        return dashboard;
    }

    /**
     * Gets all teachers assigned to courses in the department.
     * @param authenticatedUser The authenticated department head user
     * @return List of DepartmentTeacherDTO
     */
    @Transactional(readOnly = true)
    public List<DepartmentTeacherDTO> getDepartmentTeachers(User authenticatedUser) {
        DepartmentHeadDetails head = departmentHeadRepository.findByUser(authenticatedUser)
                .orElseThrow(() -> new ResourceNotFoundException("Department head profile not found"));
        
        Department department = head.getDepartment();
        List<Course> departmentCourses = courseRepo.findByDepartment(department);
        
        // Get all unique teachers assigned to department courses
        Map<TeacherDetail, Long> teacherCourseCounts = new HashMap<>();
        for (Course course : departmentCourses) {
            List<TeacherCourseAssignment> assignments = teacherCourseAssignmentRepository.findAll().stream()
                    .filter(tca -> tca.getCourse().getCID().equals(course.getCID()))
                    .collect(Collectors.toList());
            
            for (TeacherCourseAssignment tca : assignments) {
                TeacherDetail teacher = tca.getTeacher();
                teacherCourseCounts.put(teacher, teacherCourseCounts.getOrDefault(teacher, 0L) + 1);
            }
        }
        
        // Build DTOs
        List<DepartmentTeacherDTO> teachers = new ArrayList<>();
        for (Map.Entry<TeacherDetail, Long> entry : teacherCourseCounts.entrySet()) {
            TeacherDetail teacher = entry.getKey();
            DepartmentTeacherDTO dto = new DepartmentTeacherDTO();
            dto.setTeacherId(teacher.getId());
            dto.setFullName(teacher.getFirstNameEnglish() + " " + teacher.getLastNameEnglish());
            dto.setTitle(teacher.getTitle());
            dto.setEmail(teacher.getEmail());
            dto.setPhoneNumber(teacher.getPhoneNumber());
            dto.setYearsOfExperience(teacher.getYearsOfExperience());
            dto.setNumberOfCourses(entry.getValue());
            teachers.add(dto);
        }
        
        
        return teachers;
    }

    @Transactional(readOnly = true)
    public byte[] getDepartmentHeadPhoto(Long id) {
        DepartmentHeadDetails details = departmentHeadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department head not found with id: " + id));
        return details.getPhoto();
    }

    @Transactional(readOnly = true)
    public byte[] getDepartmentHeadDocument(Long id) {
        DepartmentHeadDetails details = departmentHeadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department head not found with id: " + id));
        return details.getDocuments();
    }

    // Why: Retrieves courses only for the department of the authenticated department head.
    // Ensures security - a head cannot see courses from other departments.
    // Calculates totalCrHrs = theoryHrs + labHrs.
    // Returns simplified map without full entity exposure.
    public List<Map<String, Object>> getMyDepartmentCourses(User currentUser) {

        // Find the department head profile
        DepartmentHeadDetails headDetails = departmentHeadRepository.findByUser(currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Department head profile not found"));

        Department department = headDetails.getDepartment();
        if (department == null) {
            throw new ResourceNotFoundException("No department assigned to this head");
        }

        // Fetch all courses for this department
        List<Course> courses = courseRepo.findByDepartment(department);

        // Map to response format
        return courses.stream()
                .map(course -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", course.getCID());
                    map.put("code", course.getCCode());
                    map.put("title", course.getCTitle());
                    map.put("totalCrHrs", course.getTheoryHrs() + course.getLabHrs());
                    map.put("classYearName", course.getClassYear() != null ? course.getClassYear().getClassYear() : null);
                    map.put("semesterName", course.getSemester() != null ? course.getSemester().getAcademicPeriod() : null);
                    List<TeacherCourseAssignment> assignments = teacherCourseAssignmentRepository.findByCourse(course);

                    List<Map<String, String>> teachersList = assignments.stream()
                            .map(ass -> {
                                TeacherDetail teacher = ass.getTeacher();
                                String fullName = teacher.getFirstNameEnglish() + " " + teacher.getLastNameEnglish();
                                String bcysName = ass.getBcys().getDisplayName(); // Adjust getter if different (e.g., getName())
                                Map<String, String> teacherMap = new HashMap<>();
                                teacherMap.put("name", fullName);
                                teacherMap.put("bcysName", bcysName);
                                return teacherMap;
                            })
                            .collect(Collectors.toList());

                    map.put("teachers", teachersList);
                    return map;
                })
                .collect(Collectors.toList());
    }

    // Add to DepartmentHeadService

    // Why: Securely fetches only students from the authenticated department head's department.
// Calculates full name in English: firstNameENG + fatherNameENG + grandfatherNameENG
// Uses getDisplayName() from BatchClassYearSemester and statusName from StudentStatus
// "Active" students are those with studentRecentStatus.statusName == "Active"
    @Transactional(readOnly = true)
    public Map<String, Object> getMyDepartmentStudents(User currentUser) {

        // Find department head profile
        DepartmentHeadDetails headDetails = departmentHeadRepository.findByUser(currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Department head profile not found"));

        Department department = headDetails.getDepartment();
        if (department == null) {
            throw new ResourceNotFoundException("No department assigned to this head");
        }

        // Fetch all students in this department
        List<StudentDetails> deptStudents = studentDetailsRepository.findByDepartmentEnrolled(department);

        // Map to response format
        List<Map<String, Object>> studentList = deptStudents.stream()
                .map(student -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", student.getId());
                    map.put("userId", student.getUser().getId());
                    map.put("studentId", student.getUser().getUsername());
                    map.put("fullName",
                            student.getFirstNameENG() + " " +
                                    student.getFatherNameENG() + " " +
                                    student.getGrandfatherNameENG());
                    map.put("recentBcysName", student.getBatchClassYearSemester().getDisplayName());
                    map.put("originalBatch", student.getBatch().getBatchName());
                    map.put("studentRecentStatusName", student.getStudentRecentStatus().getStatusName());
                    map.put("phoneNumber", student.getPhoneNumber());
                    map.put("gender", student.getGender().name());
                    map.put("cgpa", student.getCgpa());
                    return map;
                })
                .collect(Collectors.toList());

        // Calculate statistics
        long totalInDept = deptStudents.size();
        long totalInCollege = studentDetailsRepository.count(); // all students in the system
        double percentage = totalInCollege > 0 ? (totalInDept * 100.0 / totalInCollege) : 0.0;

        long maleCount = deptStudents.stream()
                .filter(s -> s.getGender() == Gender.MALE)
                .count();
        long femaleCount = totalInDept - maleCount;

        long activeCount = deptStudents.stream()
                .filter(s -> "Active".equals(s.getStudentRecentStatus().getStatusName()))
                .count();
        long inactiveCount = totalInDept - activeCount;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStudentsInDepartment", totalInDept);
        stats.put("totalStudentsInCollege", totalInCollege);
        stats.put("percentageOfCollege", Math.round(percentage * 100.0) / 100.0); // 2 decimal places
        stats.put("maleCount", maleCount);
        stats.put("femaleCount", femaleCount);
        stats.put("activeStudentsCount", activeCount);
        stats.put("inactiveStudentsCount", inactiveCount);

        // Final response
        Map<String, Object> response = new HashMap<>();
        response.put("students", studentList);
        response.put("statistics", stats);

        return response;
    }

    /**
     * Gets all AssessmentScoresResponse for approved assessments by teachers in the department.
     * @param authenticatedUser The authenticated department head user
     * @return List of AssessmentScoresResponse
     */
    @Transactional(readOnly = true)
    public List<AssessmentScoresResponse> getApprovedAssessmentScores(User authenticatedUser) {
        DepartmentHeadDetails head = departmentHeadRepository.findByUser(authenticatedUser)
                .orElseThrow(() -> new ResourceNotFoundException("Department head profile not found"));
        
        Department department = head.getDepartment();
        List<Course> departmentCourses = courseRepo.findByDepartment(department);
        
        // Get all assessments with assStatus = ACCEPTED for department courses
        List<Assessment> approvedAssessments = assessmentRepository.findAll().stream()
                .filter(a -> a.getAssStatus() == AssessmentStatus.ACCEPTED)
                .filter(a -> departmentCourses.contains(a.getTeacherCourseAssignment().getCourse()))
                .collect(Collectors.toList());
        
        // Group by teacher course assignment
        Map<TeacherCourseAssignment, List<Assessment>> byAssignment = approvedAssessments.stream()
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
            
            // Build assessment infos
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
                        info.setRegistrarApproval(a.getRegistrarApproval());
                        return info;
                    })
                    .collect(Collectors.toList());
            response.setAssessments(assessmentInfos);
            
            // Get students enrolled in this course and BCYS
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
                        
                        // Get scores for each assessment
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
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            
            response.setStudents(studentViews);
            responses.add(response);
        }
        
        return responses;
    }

    /**
     * Approves or rejects an assessment as department head.
     * @param authenticatedUser The authenticated department head user
     * @param status ACCEPTED or REJECTED
     * @return Updated assessment
     */
    // Updated Service method in DepartmentHeadService (or wherever it lives)
    @Transactional
    public List<Assessment> approveOrRejectAllAssessmentsInAssignment(
            User authenticatedUser,
            Long teacherCourseAssignmentId,
            AssessmentStatus status) {

        // Validate status parameter
        if (status != AssessmentStatus.ACCEPTED && status != AssessmentStatus.REJECTED) {
            throw new IllegalArgumentException("Status must be ACCEPTED or REJECTED");
        }

        // Verify user is department head
        DepartmentHeadDetails head = departmentHeadRepository.findByUser(authenticatedUser)
                .orElseThrow(() -> new ResourceNotFoundException("Department head profile not found"));

        Department department = head.getDepartment();

        // Load the TeacherCourseAssignment
        TeacherCourseAssignment tca = teacherCourseAssignmentRepository.findById(teacherCourseAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher course assignment not found"));

        // Verify the course belongs to this department head's department
        Course course = tca.getCourse();
        if (!course.getDepartment().getDptID().equals(department.getDptID())) {
            throw new IllegalArgumentException("You are not authorized to manage assessments for this department");
        }

        // Get all assessments under this teacherCourseAssignment
        List<Assessment> assessments = assessmentRepository.findByTeacherCourseAssignment(tca);

        if (assessments.isEmpty()) {
            throw new ResourceNotFoundException("No assessments found for this course assignment");
        }

        // Check that ALL assessments have already been approved by the teacher (assStatus = ACCEPTED)
        // Why: Department head can only act after teacher has approved them
        for (Assessment a : assessments) {
            if (a.getAssStatus() != AssessmentStatus.ACCEPTED) {
                throw new IllegalArgumentException(
                        "All assessments must be approved by the teacher first. Assessment '" + a.getAssTitle() + "' is not approved."
                );
            }
        }

        List<Assessment> updatedAssessments = new ArrayList<>();

        for (Assessment assessment : assessments) {
            // Set head approval
            assessment.setHeadApproval(status);

            // If rejected → revert assStatus back to PENDING
            if (status == AssessmentStatus.REJECTED) {
                assessment.setAssStatus(AssessmentStatus.PENDING);
            }
            // If accepted → assStatus remains ACCEPTED (already set by teacher)

            updatedAssessments.add(assessmentRepository.save(assessment));
        }

        // If approved → create one notification for the entire assignment
        if (status == AssessmentStatus.ACCEPTED) {
            String message = createBulkNotificationMessage(course, tca, assessments.size());

            notificationService.createNotification(
                    List.of(Role.REGISTRAR),
                    null,
                    Role.DEPARTMENT_HEAD,
                    message
            );
        }

        return updatedAssessments;
    }

    // Helper method for bulk notification message
    private String createBulkNotificationMessage(Course course, TeacherCourseAssignment tca, int assessmentCount) {
        String courseCode = course.getCCode();
        String courseTitle = course.getCTitle();
        String teacherName = tca.getTeacher().getFirstNameEnglish() + " " + tca.getTeacher().getLastNameEnglish();

        return String.format(
                "Department head has approved all %d assessments for course %s (%s) taught by %s. Please review.",
                assessmentCount,
                courseCode,
                courseTitle,
                teacherName
        );
    }
}
