package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.AssignTeacherCoursesRequest;
import Henok.example.DeutscheCollageBack_endAPI.DTO.AssignedCourseDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.UserRegisterRequest;
import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherListDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherPortal.TeacherCoursesResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherPortal.TeacherDashboardResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherPortal.TeacherProfileResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherPortal.TeacherStudentsResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.TeacherRegisterRequest;
import Henok.example.DeutscheCollageBack_endAPI.DTO.TeacherResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Impairment;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Region;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Woreda;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Zone;
import Henok.example.DeutscheCollageBack_endAPI.Enums.AssessmentStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.ImpairmentRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.RegionRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.WoredaRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.ZoneRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final UserService userService;
    private final TeacherRepository teacherRepository;
    private final TeacherCourseAssignmentRepository assignmentRepository;
    private final AssessmentRepo assessmentRepository;

    private final DepartmentRepo departmentRepository;
    private final WoredaRepository woredaRepository;
    private final ZoneRepository zoneRepository;
    private final RegionRepository regionRepository;
    private final ImpairmentRepository impairmentRepository;

    private final CourseRepo courseRepository;
    private final BatchClassYearSemesterRepo bcysRepository;

    private final StudentCourseScoreRepo studentCourseScoreRepository;
    private final StudentDetailsRepository studentDetailsRepository;

    private final EntityManager entityManager;

    // ==================== REGISTER TEACHER + ASSIGN COURSES ====================
    @Transactional
    public TeacherDetail registerTeacher(TeacherRegisterRequest request,
                                         MultipartFile photograph,
                                         MultipartFile document) {

        // --- VALIDATION (same as before) ---
        validateRegistrationRequest(request);
        if (photograph != null && !photograph.isEmpty() && photograph.getSize() > 2_000_000) { // 2MB limit
            throw new IllegalArgumentException("Student photo size exceeds 2MB limit");
        }
        if (document != null && !document.isEmpty() && document.getSize() > 10_000_000) { // 10MB limit
            throw new IllegalArgumentException("Document size exceeds 10MB limit");
        }

        // --- CREATE USER ---
        UserRegisterRequest userReq = new UserRegisterRequest();
        userReq.setUsername(request.getUsername());
        userReq.setPassword(request.getPassword());
        userReq.setRole(Role.TEACHER);
        User user = userService.registerUser(userReq);
        entityManager.flush();

        if (teacherRepository.findByUser(user).isPresent())
            throw new IllegalArgumentException("Teacher detail already exists for user: " + user.getUsername());

        // --- BUILD TEACHER DETAIL ---
        TeacherDetail teacher = buildTeacherDetail(request, user, photograph, document);
        teacher = teacherRepository.save(teacher);
        entityManager.flush();

        // --- ASSIGN COURSES (if any) ---
        if (request.getCourseAssignments() != null && !request.getCourseAssignments().isEmpty()) {
            assignCoursesToTeacher(teacher.getId(), request.getCourseAssignments());
        }

        entityManager.clear();
        return teacher;
    }

    // Reusable method for assigning courses
    private void assignCoursesToTeacher(Long teacherId, List<AssignTeacherCoursesRequest> requests) {
        TeacherDetail teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalStateException("Teacher not found after save"));

        for (AssignTeacherCoursesRequest req : requests) {
            Course course = courseRepository.findById(req.getCourseId())
                    .orElseThrow(() -> new IllegalArgumentException("Course not found: " + req.getCourseId()));

            BatchClassYearSemester bcys = bcysRepository.findById(req.getBcysId())
                    .orElseThrow(() -> new IllegalArgumentException("BCYS not found: " + req.getBcysId()));

            if (assignmentRepository.existsByTeacherAndCourseAndBcys(teacher, course, bcys)) {
                throw new IllegalArgumentException(
                        "Teacher already assigned to course " + course.getCTitle() +
                                " in " + bcys.getDisplayName());
            }

            TeacherCourseAssignment assignment = new TeacherCourseAssignment();
            assignment.setTeacher(teacher);
            assignment.setCourse(course);
            assignment.setBcys(bcys);
            assignment.setAssignedAt(LocalDateTime.now());

            assignmentRepository.save(assignment);
        }
    }

    // Extracted validation
    private void validateRegistrationRequest(TeacherRegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isEmpty())
            throw new IllegalArgumentException("Username cannot be empty");
        if (request.getPassword() == null || request.getPassword().isEmpty())
            throw new IllegalArgumentException("Password cannot be empty");
        if (request.getFirstNameAmharic() == null || request.getFirstNameAmharic().isEmpty())
            throw new IllegalArgumentException("First name in Amharic cannot be empty");
        if (request.getLastNameAmharic() == null || request.getLastNameAmharic().isEmpty())
            throw new IllegalArgumentException("Last name in Amharic cannot be empty");
        if (request.getFirstNameEnglish() == null || request.getFirstNameEnglish().isEmpty())
            throw new IllegalArgumentException("First name in English cannot be empty");
        if (request.getLastNameEnglish() == null || request.getLastNameEnglish().isEmpty())
            throw new IllegalArgumentException("Last name in English cannot be empty");
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isEmpty())
            throw new IllegalArgumentException("Phone number cannot be empty");
        
        if (request.getDepartmentId() == null)
            throw new IllegalArgumentException("Department is required");

        if (teacherRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent())
            throw new IllegalArgumentException("Phone number already exists");

    }

    // Extracted teacher building
    private TeacherDetail buildTeacherDetail(TeacherRegisterRequest request, User user,
                                             MultipartFile photograph, MultipartFile document) {
        TeacherDetail teacher = new TeacherDetail();
        teacher.setUser(user);

        teacher.setFirstNameAmharic(request.getFirstNameAmharic());
        teacher.setLastNameAmharic(request.getLastNameAmharic());
        teacher.setFirstNameEnglish(request.getFirstNameEnglish());
        teacher.setLastNameEnglish(request.getLastNameEnglish());
        teacher.setGender(request.getGender());
        teacher.setDateOfBirthGC(request.getDateOfBirthGC());
        teacher.setDateOfBirthEC(request.getDateOfBirthEC());
        teacher.setPhoneNumber(request.getPhoneNumber());
        teacher.setEmail(request.getEmail());

        Department dept = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid department ID"));
        teacher.setDepartment(dept);

        teacher.setHireDateGC(request.getHireDateGC());
        teacher.setHireDateEC(request.getHireDateEC());
        teacher.setResignedDate(request.getResignedDate());
        teacher.setTitle(request.getTitle());
        teacher.setYearsOfExperience(request.getYearsOfExperience());

        if (request.getImpairmentCode() != null && !request.getImpairmentCode().isEmpty()) {
            Impairment imp = impairmentRepository.findById(request.getImpairmentCode())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid impairment code"));
            teacher.setImpairment(imp);
        }
        teacher.setMaritalStatus(request.getMaritalStatus());

        if (request.getCurrentAddressWoredaCode() != null && !request.getCurrentAddressWoredaCode().isEmpty()) {
            Woreda w = woredaRepository.findById(request.getCurrentAddressWoredaCode())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid woreda code"));
            teacher.setCurrentAddressWoreda(w);
        }
        if (request.getCurrentAddressZoneCode() != null && !request.getCurrentAddressZoneCode().isEmpty()) {
            Zone z = zoneRepository.findById(request.getCurrentAddressZoneCode())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid zone code"));
            teacher.setCurrentAddressZone(z);
        }
        if (request.getCurrentAddressRegionCode() != null && !request.getCurrentAddressRegionCode().isEmpty()) {
            Region r = regionRepository.findById(request.getCurrentAddressRegionCode())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid region code"));
            teacher.setCurrentAddressRegion(r);
        }

        try {
            if (photograph != null && !photograph.isEmpty())
                teacher.setPhotograph(photograph.getBytes());
            if (document != null && !document.isEmpty())
                teacher.setDocuments(document.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to process files: " + e.getMessage());
        }

        return teacher;
    }

    // ==================== GET ALL (reduced info) ====================
    @Transactional(readOnly = true)
    public List<TeacherListDTO> getAllTeachers() {
        return teacherRepository.findAll().stream()
                .map(this::toListDto)
                .toList();
    }

    // ==================== GET BY ID (full details + courses) ====================
    @Transactional(readOnly = true)
    public TeacherResponseDTO getTeacherById(Long id) {
        TeacherDetail t = teacherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found with ID: " + id));
        return toDetailDto(t);
    }

    // ==================== GET FILES ====================
    @Transactional(readOnly = true)
    public byte[] getTeacherPhoto(Long id) {
        TeacherDetail t = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with ID: " + id));
        return t.getPhotograph();
    }

    @Transactional(readOnly = true)
    public byte[] getTeacherDocument(Long id) {
        TeacherDetail t = teacherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with ID: " + id));
        return t.getDocuments();
    }

    // ==================== PRIVATE MAPPERS ====================

    private TeacherListDTO toListDto(TeacherDetail t) {
        TeacherListDTO dto = new TeacherListDTO();
        dto.setTeacherId(t.getId());
        dto.setTeacherUserId(t.getUser().getId());
        dto.setFullNameAmharic(t.getFirstNameAmharic() + " " + t.getLastNameAmharic());
        dto.setFullNameEnglish(t.getFirstNameEnglish() + " " + t.getLastNameEnglish());
        dto.setDepartmentName(t.getDepartment().getDeptName());
        dto.setTitle(t.getTitle());
        dto.setEmail(t.getEmail());
        dto.setPhoneNumber(t.getPhoneNumber());
        dto.setAssignedCoursesCount(assignmentRepository.findByTeacher(t).size());
        dto.setAccountStatus(t.getUser().isEnabled() ? "Active" : "Disabled");

        if (t.getPhotograph() != null && t.getPhotograph().length > 0) {
            dto.setPhotographBase64(Base64.getEncoder().encodeToString(t.getPhotograph()));
        }

        return dto;
    }

    // TeacherService.java (updated toDetailDto method only)
    public TeacherResponseDTO toDetailDto(TeacherDetail t) {
        TeacherResponseDTO dto = new TeacherResponseDTO();
        dto.setTeacherId(t.getId());

        dto.setUserId(t.getUser().getId());
        dto.setUsername(t.getUser().getUsername());

        dto.setFirstNameAmharic(t.getFirstNameAmharic());
        dto.setLastNameAmharic(t.getLastNameAmharic());
        dto.setFirstNameEnglish(t.getFirstNameEnglish());
        dto.setLastNameEnglish(t.getLastNameEnglish());

        dto.setGender(t.getGender());
        dto.setDateOfBirthGC(t.getDateOfBirthGC());
        dto.setDateOfBirthEC(t.getDateOfBirthEC());

        dto.setPhoneNumber(t.getPhoneNumber());
        dto.setEmail(t.getEmail());

        dto.setDepartmentName(t.getDepartment().getDeptName());
        dto.setHireDateGC(t.getHireDateGC());
        dto.setHireDateEC(t.getHireDateEC());
        dto.setTitle(t.getTitle());
        dto.setYearsOfExperience(t.getYearsOfExperience());

        if (t.getImpairment() != null) {
            dto.setImpairmentCode(t.getImpairment().getImpairmentCode());      // assuming Impairment entity has getCode()
            dto.setImpairment(t.getImpairment().getImpairment()); // human-readable name
        }        dto.setMaritalStatus(t.getMaritalStatus());

        // --- Separate address fields ---
        if (t.getCurrentAddressWoreda() != null) {
            dto.setWoredaCode(t.getCurrentAddressWoreda().getWoredaCode());   // assuming Woreda has getCode()
            dto.setWoredaName(t.getCurrentAddressWoreda().getWoreda());
        }
        if (t.getCurrentAddressZone() != null) {
            dto.setZoneCode(t.getCurrentAddressZone().getZoneCode());       // assuming Zone has getCode()
            dto.setZoneName(t.getCurrentAddressZone().getZone());
        }
        if (t.getCurrentAddressRegion() != null) {
            dto.setRegionCode(t.getCurrentAddressRegion().getRegionCode());   // assuming Region has getCode()
            dto.setRegionName(t.getCurrentAddressRegion().getRegion());
        }

        if (t.getPhotograph() != null && t.getPhotograph().length > 0) {
            dto.setPhotographBase64(Base64.getEncoder().encodeToString(t.getPhotograph()));
        }

        // Assigned courses
        List<AssignedCourseDTO> courses = assignmentRepository.findByTeacher(t).stream()
                .map(this::toAssignedCourseDto)
                .toList();
        dto.setAssignedCourses(courses);

        return dto;
    }

    private AssignedCourseDTO toAssignedCourseDto(TeacherCourseAssignment a) {
        AssignedCourseDTO dto = new AssignedCourseDTO();
        Course c = a.getCourse();
        BatchClassYearSemester b = a.getBcys();

        dto.setTeacherCourseAssigmentId(a.getId());
        dto.setId(c.getCID());
        dto.setCourseCode(c.getCCode());  // Assuming Course has getCode()
        dto.setCourseTitle(c.getCTitle());  // Assuming Course has getTitle()
        dto.setTotalCrHrs(c.getLabHrs() + c.getTheoryHrs());  // Assuming Course has getCreditHours() as total; adjust if lab + theory separate
        dto.setBatchClassYearSemesterId(b.getBcysID());
        dto.setBatchClassYearSemesterName(b.getDisplayName());

        return dto;
    }

    @Transactional(readOnly = true)
    public TeacherProfileResponse getTeacherProfileByUser(User user) {
        TeacherDetail details = teacherRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found for user: " + user.getUsername()));

        // Build full names
        String fullNameEnglish = String.join(" ", details.getFirstNameEnglish(), details.getLastNameEnglish()).trim();
        String fullNameAmharic = String.join(" ", details.getFirstNameAmharic(), details.getLastNameAmharic()).trim();

        return TeacherProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullNameEnglish(fullNameEnglish)
                .fullNameAmharic(fullNameAmharic)
                .gender(details.getGender())                          // enum
                .dateOfBirthGC(details.getDateOfBirthGC())
                .dateOfBirthEC(details.getDateOfBirthEC())
                .phoneNumber(details.getPhoneNumber())
                .email(details.getEmail())
                .maritalStatus(details.getMaritalStatus())            // enum
                .photoBase64(details.getPhotograph() != null
                        ? Base64.getEncoder().encodeToString(details.getPhotograph())
                        : null)
                // Current address (flattened)
                .currentAddressRegion(details.getCurrentAddressRegion() != null ? details.getCurrentAddressRegion().getRegion() : null)
                .currentAddressZone(details.getCurrentAddressZone() != null ? details.getCurrentAddressZone().getZone() : null)
                .currentAddressWoreda(details.getCurrentAddressWoreda() != null ? details.getCurrentAddressWoreda().getWoreda() : null)
                // Related entities – only names
                .impairment(details.getImpairment() != null ? details.getImpairment().getImpairment() : null)
                .department(details.getDepartment().getDeptName())
                .title(details.getTitle())
                .yearsOfExperience(details.getYearsOfExperience())
                .hireDateGC(details.getHireDateGC())
                .hireDateEC(details.getHireDateEC())
                .build();
    }

    // ==================== UPDATE (partial) ====================
    @Transactional
    public TeacherDetail updateTeacher(Long id, TeacherRegisterRequest request,
                                       MultipartFile photograph, MultipartFile document) {
        TeacherDetail teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found with ID: " + id));

        // ... existing admin update logic (if needed, or leave as is)
        // For self update we generally don't use this method if it allows changing restricted things.
        // But the previous implementation seems to cover general updates.
        // We will add a NEW method for SELF update to strictly enforce the new rules.
        
        // REUSING existing update logic might be dangerous if requests contain restricted fields.
        // The prompt asks for a specific logic for self-update.
        // I will keep this existing method for ADMIN/General usage and add a specific one below.
        
        return updateTeacherCommon(teacher, request, photograph, document);
    }
    
    // Extracted common update logic but we'll create a specific one for self-update to be safe/clean
    private TeacherDetail updateTeacherCommon(TeacherDetail teacher, TeacherRegisterRequest request, MultipartFile photograph, MultipartFile document) {
         if (request.getUsername() != null && !request.getUsername().equals(teacher.getUser().getUsername()))
            throw new IllegalArgumentException("Cannot change username");

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(teacher.getPhoneNumber())) {
            if (teacherRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent())
                throw new IllegalArgumentException("Phone number already in use");
        }

        ofNullable(request.getFirstNameAmharic()).filter(s -> !s.isEmpty()).ifPresent(teacher::setFirstNameAmharic);
        ofNullable(request.getLastNameAmharic()).filter(s -> !s.isEmpty()).ifPresent(teacher::setLastNameAmharic);
        ofNullable(request.getFirstNameEnglish()).filter(s -> !s.isEmpty()).ifPresent(teacher::setFirstNameEnglish);
        ofNullable(request.getLastNameEnglish()).filter(s -> !s.isEmpty()).ifPresent(teacher::setLastNameEnglish);
        ofNullable(request.getGender()).ifPresent(teacher::setGender);
        ofNullable(request.getDateOfBirthGC()).ifPresent(teacher::setDateOfBirthGC);
        ofNullable(request.getDateOfBirthEC()).filter(s -> !s.isEmpty()).ifPresent(teacher::setDateOfBirthEC);
        ofNullable(request.getPhoneNumber()).filter(s -> !s.isEmpty()).ifPresent(teacher::setPhoneNumber);
        ofNullable(request.getEmail()).ifPresent(e -> teacher.setEmail(e.isEmpty() ? null : e));

        ofNullable(request.getDepartmentId()).ifPresent(deptId -> {
            Department d = departmentRepository.findById(deptId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid department ID"));
            teacher.setDepartment(d);
        });

        ofNullable(request.getHireDateGC()).ifPresent(teacher::setHireDateGC);
        ofNullable(request.getResignedDate()).ifPresent(teacher::setResignedDate);

        ofNullable(request.getHireDateEC()).filter(s -> !s.isEmpty()).ifPresent(teacher::setHireDateEC);
        ofNullable(request.getTitle()).ifPresent(t -> teacher.setTitle(t.isEmpty() ? null : t));
        ofNullable(request.getYearsOfExperience()).ifPresent(teacher::setYearsOfExperience);

        ofNullable(request.getImpairmentCode()).ifPresent(code -> {
            if (code.isEmpty()) teacher.setImpairment(null);
            else {
                Impairment imp = impairmentRepository.findById(code)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid impairment code"));
                teacher.setImpairment(imp);
            }
        });

        ofNullable(request.getMaritalStatus()).ifPresent(teacher::setMaritalStatus);

        ofNullable(request.getCurrentAddressWoredaCode()).ifPresent(code -> {
            if (code.isEmpty()) teacher.setCurrentAddressWoreda(null);
            else {
                Woreda w = woredaRepository.findById(code)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid woreda code"));
                teacher.setCurrentAddressWoreda(w);
            }
        });
        ofNullable(request.getCurrentAddressZoneCode()).ifPresent(code -> {
            if (code.isEmpty()) teacher.setCurrentAddressZone(null);
            else {
                Zone z = zoneRepository.findById(code)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid zone code"));
                teacher.setCurrentAddressZone(z);
            }
        });
        ofNullable(request.getCurrentAddressRegionCode()).ifPresent(code -> {
            if (code.isEmpty()) teacher.setCurrentAddressRegion(null);
            else {
                Region r = regionRepository.findById(code)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid region code"));
                teacher.setCurrentAddressRegion(r);
            }
        });

        try {

            if (photograph != null && !photograph.isEmpty() && photograph.getSize() > 2_000_000) { // 2MB limit
                throw new IllegalArgumentException("Student photo size exceeds 2MB limit");
            }
            if (document != null && !document.isEmpty() && document.getSize() > 10_000_000) { // 10MB limit
                throw new IllegalArgumentException("Document size exceeds 10MB limit");
            }

            if (photograph != null && !photograph.isEmpty())
                teacher.setPhotograph(photograph.getBytes());
            if (document != null && !document.isEmpty())
                teacher.setDocuments(document.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to process files: " + e.getMessage());
        }

        return teacherRepository.save(teacher);
    }
    
    @Transactional
    public TeacherResponseDTO updateTeacherSelf(User user, TeacherRegisterRequest request, MultipartFile photograph) {
        TeacherDetail teacher = teacherRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));

        // Allowed Updates: Names, Gender, Phone, Email, Title, Impairment, MaritalStatus, Address, Photo
        // Restricted: Documents, HiredDate, DOB, Experience, Department, UserInfo (username/password/role)

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isEmpty() 
                && !request.getPhoneNumber().equals(teacher.getPhoneNumber())) {
            if (teacherRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent())
                throw new IllegalArgumentException("Phone number already in use");
            teacher.setPhoneNumber(request.getPhoneNumber());
        }

        ofNullable(request.getFirstNameAmharic()).filter(s -> !s.isEmpty()).ifPresent(teacher::setFirstNameAmharic);
        ofNullable(request.getLastNameAmharic()).filter(s -> !s.isEmpty()).ifPresent(teacher::setLastNameAmharic);
        ofNullable(request.getFirstNameEnglish()).filter(s -> !s.isEmpty()).ifPresent(teacher::setFirstNameEnglish);
        ofNullable(request.getLastNameEnglish()).filter(s -> !s.isEmpty()).ifPresent(teacher::setLastNameEnglish);
        
        ofNullable(request.getGender()).ifPresent(teacher::setGender);
        
        // DOB IS RESTRICTED for Teacher self-update per prompt "date of birth"
        // Hired Date IS RESTRICTED per prompt
        // Experience IS RESTRICTED per prompt
        // Department IS RESTRICTED per prompt
        // Documents ARE RESTRICTED per prompt
        
        ofNullable(request.getEmail()).ifPresent(e -> teacher.setEmail(e.isEmpty() ? null : e));
        ofNullable(request.getTitle()).ifPresent(t -> teacher.setTitle(t.isEmpty() ? null : t));
        ofNullable(request.getMaritalStatus()).ifPresent(teacher::setMaritalStatus);

        ofNullable(request.getImpairmentCode()).ifPresent(code -> {
            if (code.isEmpty()) teacher.setImpairment(null);
            else {
                Impairment imp = impairmentRepository.findById(code)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid impairment code"));
                teacher.setImpairment(imp);
            }
        });

        ofNullable(request.getCurrentAddressWoredaCode()).ifPresent(code -> {
            if (code.isEmpty()) teacher.setCurrentAddressWoreda(null);
            else {
                Woreda w = woredaRepository.findById(code)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid woreda code"));
                teacher.setCurrentAddressWoreda(w);
            }
        });
        ofNullable(request.getCurrentAddressZoneCode()).ifPresent(code -> {
            if (code.isEmpty()) teacher.setCurrentAddressZone(null);
            else {
                Zone z = zoneRepository.findById(code)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid zone code"));
                teacher.setCurrentAddressZone(z);
            }
        });
        ofNullable(request.getCurrentAddressRegionCode()).ifPresent(code -> {
            if (code.isEmpty()) teacher.setCurrentAddressRegion(null);
            else {
                Region r = regionRepository.findById(code)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid region code"));
                teacher.setCurrentAddressRegion(r);
            }
        });

        try {
            if (photograph != null && !photograph.isEmpty())
                teacher.setPhotograph(photograph.getBytes());
            // Intentionally ignoring 'document' update here
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to process photograph: " + e.getMessage());
        }

        TeacherDetail updated = teacherRepository.save(teacher);
        return toDetailDto(updated);
    }

    // ==================== DELETE ====================
    @Transactional
    public void deleteTeacher(Long id) {
        TeacherDetail teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found with ID: " + id));

        assignmentRepository.findByTeacher(teacher).forEach(assignmentRepository::delete);
//        userService.deleteUser(teacher.getUser().getId());
        teacherRepository.delete(teacher);
    }


    // Returns all distinct students currently being taught by the authenticated teacher
    // Why: A teacher may teach multiple courses in the same or different BCYS
    //      We collect all StudentCourseScore entries for those assignments and extract unique students
    @Transactional(readOnly = true)
    public TeacherStudentsResponse getStudentsTaughtByTeacher(User authenticatedUser) {

        // Step 1: Get TeacherDetail from authenticated user
        TeacherDetail teacher = teacherRepository.findByUser(authenticatedUser)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));

        // Step 2: Find all course assignments for this teacher
        List<TeacherCourseAssignment> assignments = assignmentRepository.findByTeacher(teacher);

        if (assignments.isEmpty()) {
            TeacherStudentsResponse emptyResponse = new TeacherStudentsResponse();
            emptyResponse.setMessage("You are not currently assigned to any course");
            emptyResponse.setTotalStudents(0);
            return emptyResponse;
        }

        // Step 3: Collect unique (course, bcys) pairs
        Set<CourseBcysPair> courseBcysPairs = assignments.stream()
                .map(tca -> new CourseBcysPair(tca.getCourse(), tca.getBcys()))
                .collect(Collectors.toSet());

        // Step 4: Get all enrollments for these course + bcys combinations
        List<StudentCourseScore> enrollments = studentCourseScoreRepository
                .findByCourseAndBatchClassYearSemesterIn(courseBcysPairs);

        // Step 5: Extract distinct students via User → StudentDetails
        List<TeacherStudentsResponse.StudentInfo> studentInfos = enrollments.stream()
                .map(StudentCourseScore::getStudent)           // → User (student)
                .distinct()
                .map(user -> {
                    // Navigate from User back to StudentDetails
                    // Assuming you have a query or derived method to get StudentDetails by User
                    StudentDetails details = studentDetailsRepository.findByUser(user)
                            .orElse(null); // should not happen if data is consistent

                    if (details == null) {
                        return null; // skip inconsistent data
                    }

                    TeacherStudentsResponse.StudentInfo info = new TeacherStudentsResponse.StudentInfo();
                    info.setStudentId(details.getId());
                    info.setStudentIdNumber(details.getUser().getUsername()); // adjust if field name differs
                    info.setFullNameENG(
                            details.getFirstNameENG() + " " +
                                    details.getFatherNameENG() + " " +
                                    details.getGrandfatherNameENG()
                    );
                    info.setFullNameAMH(
                            details.getFirstNameAMH() + " " +
                                    details.getFatherNameAMH() + " " +
                                    details.getGrandfatherNameAMH()
                    );
                    info.setDepartment(details.getDepartmentEnrolled().getDeptName());
                    info.setProgram(details.getProgramModality().getModality());
                    return info;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TeacherStudentsResponse.StudentInfo::getFullNameENG))
                .toList();

        // Step 6: Build response
        TeacherStudentsResponse response = new TeacherStudentsResponse();
        response.setMessage("Students retrieved successfully");
        response.setTotalStudents(studentInfos.size());
        response.setStudents(studentInfos);

        return response;
    }

    // Add this method to your TeacherService
    @Transactional(readOnly = true)
    public TeacherStudentsResponse getStudentsByCourseAssignment(User authenticatedUser, Long teacherCourseAssignmentId) {

        // Step 1: Get TeacherDetail from authenticated user
        TeacherDetail teacher = teacherRepository.findByUser(authenticatedUser)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));

        // Step 2: Load the specific TeacherCourseAssignment and verify ownership
        TeacherCourseAssignment tca = assignmentRepository.findById(teacherCourseAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Course assignment not found"));

        if (!tca.getTeacher().getId().equals(teacher.getId())) {
            throw new IllegalArgumentException("You are not assigned to this course");
        }

        // Step 3: Get all enrollments for this exact course + BCYS
        List<StudentCourseScore> enrollments = studentCourseScoreRepository
                .findByCourseAndBatchClassYearSemester(tca.getCourse(), tca.getBcys());

        // Handle null or empty safely
        if (enrollments == null) {
            enrollments = Collections.emptyList();
        }

        // Step 4: Extract distinct students and map to DTO
        List<TeacherStudentsResponse.StudentInfo> studentInfos = enrollments.stream()
                .map(StudentCourseScore::getStudent) // → User (student)
                .distinct()
                .map(user -> {
                    StudentDetails details = studentDetailsRepository.findByUser(user)
                            .orElse(null); // data inconsistency guard

                    if (details == null) {
                        return null; // skip bad data
                    }

                    TeacherStudentsResponse.StudentInfo info = new TeacherStudentsResponse.StudentInfo();
                    info.setStudentId(details.getId());
                    info.setStudentIdNumber(details.getUser().getUsername());
                    info.setFullNameENG(
                            details.getFirstNameENG() + " " +
                                    details.getFatherNameENG() + " " +
                                    details.getGrandfatherNameENG()
                    );
                    info.setFullNameAMH(
                            details.getFirstNameAMH() + " " +
                                    details.getFatherNameAMH() + " " +
                                    details.getGrandfatherNameAMH()
                    );
                    info.setEmail(details.getEmail() == null ?  "No Email" : details.getEmail());
                    info.setDepartment(details.getDepartmentEnrolled().getDeptName());
                    info.setProgram(details.getProgramModality().getModality());
                    return info;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TeacherStudentsResponse.StudentInfo::getFullNameENG))
                .toList();

        // Step 5: Build response
        TeacherStudentsResponse response = new TeacherStudentsResponse();
        response.setMessage("Students retrieved successfully for course assignment " + teacherCourseAssignmentId);
        response.setTotalStudents(studentInfos.size());
        response.setStudents(studentInfos);

        return response;
    }

    // Returns all current course assignments for the authenticated teacher
    // Why: Uses TeacherCourseAssignment as the source of truth for what a teacher teaches
    @Transactional(readOnly = true)
    public TeacherCoursesResponse getAssignedCourses(User authenticatedUser) {

        // Step 1: Retrieve TeacherDetail from authenticated User
        TeacherDetail teacher = teacherRepository.findByUser(authenticatedUser)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));

        // Step 2: Fetch all assignments for this teacher
        List<TeacherCourseAssignment> assignments = assignmentRepository.findByTeacher(teacher);

        // Step 3: Map to response DTO (nested class defined inside)
        List<TeacherCoursesResponse.CourseAssignment> courseList = assignments.stream()
                .map(tca -> {
                    TeacherCoursesResponse.CourseAssignment ca = new TeacherCoursesResponse.CourseAssignment();
                    ca.setAssignmentId(tca.getId());
                    ca.setCourseCode(tca.getCourse().getCCode());
                    ca.setCourseTitle(tca.getCourse().getCTitle());
                    ca.setTheoryHours(tca.getCourse().getTheoryHrs());
                    ca.setLabHours(tca.getCourse().getLabHrs());
                    ca.setDepartment(tca.getCourse().getDepartment().getDeptName());
                    ca.setBatchClassYearSemester(tca.getBcys().getDisplayName()); // assuming display method exists
                    ca.setAssignedAt(tca.getAssignedAt());
                    return ca;
                })
                .sorted(Comparator.comparing(TeacherCoursesResponse.CourseAssignment::getCourseCode))
                .toList();

        // Step 4: Build final response
        TeacherCoursesResponse response = new TeacherCoursesResponse();
        response.setMessage("Assigned courses retrieved successfully");
        response.setTotalCourses(courseList.size());
        response.setCourses(courseList);

        return response;
    }

    // Helper record to pair Course + BatchClassYearSemester for query
    public record CourseBcysPair(Course course, BatchClassYearSemester bcys) {}

    // Inside TeacherService class
    // Add this method to your existing TeacherService
    // Why: Provides dashboard overview without separate service class
    public TeacherDashboardResponse getDashboardData(User authenticatedUser) {

        // Step 1: Get TeacherDetail from authenticated user
        TeacherDetail teacher = teacherRepository.findByUser(authenticatedUser)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));

        // Step 2: Get all course assignments for this teacher
        List<TeacherCourseAssignment> assignments = assignmentRepository.findByTeacher(teacher);
        // Handle null/empty safely
        if (assignments == null) {
            assignments = Collections.emptyList();
        }

        // Step 3: Count total distinct students across all teacher's courses
        // Why: Uses joined query to get distinct students from StudentCourseScore matching teacher's assignments
        int totalStudents = studentCourseScoreRepository.countDistinctStudentsByTeacher(teacher);

        // Step 4: Count assessments – queries assessment table by teacher's assignments via joined teacher filter
        long totalAssessments = assessmentRepository.countByTeacher(teacher);

        // Count pending assessments – same filter + status
        long pendingAssessments = assessmentRepository.countByTeacherAndAssStatus(teacher, AssessmentStatus.PENDING);

        // Step 5: Build recent courses list
        // Why: For each assignment, fetches student count from StudentCourseScore matching (course, BCYS)
        // Handles empty assignments safely
        List<TeacherDashboardResponse.CourseSummary> recentCourses = new ArrayList<>();
        for (TeacherCourseAssignment tca : assignments) {
            // Safe null checks for course and bcys
            Course course = tca.getCourse();
            BatchClassYearSemester bcys = tca.getBcys();
            if (course == null || bcys == null) {
                continue; // Skip invalid assignments
            }

            // Count students enrolled in this specific (course, BCYS)
            long studentCount = studentCourseScoreRepository.countByCourseAndBatchClassYearSemester(course, bcys);

            TeacherDashboardResponse.CourseSummary summary = new TeacherDashboardResponse.CourseSummary();
            summary.setAssignmentId(tca.getId());
            summary.setCourseCode(course.getCCode());
            summary.setCourseTitle(course.getCTitle());
            summary.setBatchClassYearSemester(bcys.getDisplayName());
            summary.setStudentCount(studentCount);
            recentCourses.add(summary);
        }
        recentCourses.sort(Comparator.comparing(TeacherDashboardResponse.CourseSummary::getCourseCode));

        // Step 6: Build response
        TeacherDashboardResponse response = new TeacherDashboardResponse();
        response.setMessage("Dashboard data retrieved successfully");
        response.setTeacherNameENG(teacher.getFirstNameEnglish() + " " + teacher.getLastNameEnglish());
        response.setTeacherNameAMH(teacher.getFirstNameAmharic() + " " + teacher.getLastNameAmharic());
        response.setDepartment(teacher.getDepartment() != null ? teacher.getDepartment().getDepartmentCode() : "N/A");
        response.setTotalAssignedCourses(assignments.size());
        response.setTotalStudents(totalStudents);
        response.setTotalAssessmentsCreated(totalAssessments);
        response.setPendingAssessments(pendingAssessments);
        response.setRecentCourses(recentCourses);

        return response;
    

    }

}