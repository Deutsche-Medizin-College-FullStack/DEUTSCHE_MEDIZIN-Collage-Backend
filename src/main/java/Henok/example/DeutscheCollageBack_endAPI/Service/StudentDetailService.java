package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.AcceptApplicationRequest;
import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.StudentRegisterRequest;
import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.UserRegisterRequest;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Student.StudentProfileResponse;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentSlips.StudentsListForSlipDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Students.*;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCGPADTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.*;
import Henok.example.DeutscheCollageBack_endAPI.Enums.ApplicationStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.DocumentStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.ExitExamPassStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Error.BadRequestException;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.*;
import Henok.example.DeutscheCollageBack_endAPI.Service.Utility.ScoreUpdatedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
public class StudentDetailService {

    @Autowired
    private StudentDetailsRepository studentDetailsRepository;
    @Autowired
    private AppliedStudentRepository appliedStudentRepository;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;

    @Autowired
    private WoredaRepository woredaRepository;
    @Autowired
    private ZoneRepository zoneRepository;
    @Autowired
    private RegionRepository regionRepository;
    @Autowired
    private ImpairmentRepository impairmentRepository;
    @Autowired
    private SchoolBackgroundRepository schoolBackgroundRepository;

    @Autowired
    private BatchClassYearSemesterRepo batchClassYearSemesterRepository;
    @Autowired
    private StudentStatusRepo studentStatusRepository;
    @Autowired
    private DepartmentRepo departmentRepository;
    @Autowired
    private BatchRepo batchRepository;
    @Autowired
    private ProgramModalityRepository programModalityRepository;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private AcademicYearRepo academicYearRepository;
    @Autowired
    private StudentCopyService studentCopyService;
    @Autowired
    private GradingSystemService gradingSystemService;

    @Autowired
    private StudentCourseScoreRepo studentCourseScoreRepo;
    @Autowired
    private CourseRepo courseRepository;



    // Registers a new student with the provided details and files
    // Why: Handles student registration with multipart form data, validates inputs, and ensures data integrity
    @Transactional(rollbackFor = Exception.class)
    public StudentDetails registerStudent(StudentRegisterRequest request, MultipartFile studentPhoto, MultipartFile document) {
//        System.out.println("================ Starting student registration process... ================");
        // Validate required fields
        validateRegistrationRequest(request);
//        System.out.println("Registration request validated successfully.");

        // Check for duplicate phone number
        if (studentDetailsRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("Phone number already in use");
        }
//        System.out.println("Phone number is unique.");
        // Check for duplicate exitExamUserID if provided
        if (request.getExitExamUserID() != null && !request.getExitExamUserID().isEmpty()
                && studentDetailsRepository.existsByExitExamUserID(request.getExitExamUserID())) {
            throw new IllegalArgumentException("Exit exam user ID already in use");
        }
//        System.out.println("Exit exam user ID is unique or not provided.");

        // Validate file sizes
        if (studentPhoto != null && !studentPhoto.isEmpty() && studentPhoto.getSize() > 2_000_000) { // 2MB limit
            throw new IllegalArgumentException("Student photo size exceeds 2MB limit");
        }
        if (document != null && !document.isEmpty() && document.getSize() > 10_000_000) { // 10MB limit
            throw new IllegalArgumentException("Document size exceeds 10MB limit");
        }
//        System.out.println("File sizes are within limits.");

        // Register user with STUDENT role
        UserRegisterRequest userRequest = new UserRegisterRequest();
        userRequest.setUsername(request.getUsername());
        userRequest.setPassword(request.getPassword());
        userRequest.setRole(Role.STUDENT);
        User user;
        try {
            user = userService.registerUser(userRequest);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("User registration failed: " + e.getMessage());
        }
//        System.out.println("User registered successfully with username: " + user.getUsername());

        // Map request to StudentDetails entity
        StudentDetails student;
        try {
            student = mapRequestToEntity(request, user, studentPhoto, document);
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Failed to map request: " + e.getMessage());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to process file uploads: " + e.getMessage());
        }
//        System.out.println("StudentDetails entity created successfully !! ");

        // Save student
        try {
            StudentDetails newStudent = studentDetailsRepository.save(student);
//            System.out.println("Student registered successfully with ID: " + newStudent.getId());
            notificationService.createNotification(Arrays.asList(
                    Role.GENERAL_MANAGER, Role.DEAN, Role.VICE_DEAN, Role.DEPARTMENT_HEAD, Role.REGISTRAR),
                    null, Role.REGISTRAR,
                    "New Student Registered : " + newStudent.getFirstNameAMH() + " " + newStudent.getFatherNameAMH());

//            System.out.println("Finally Sent Notifications");
            return newStudent;
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Failed to register student due to duplicate entry or constraint violation: " + e.getMessage());
        }
    }

    // Retrieves all students as DTOs, including disabled ones
    // Why: For admin/registrar to view all student records, regardless of enabled status
    public List<StudentListDTO> getAllStudents() {
        return studentDetailsRepository.findAll().stream()
                .map(student -> {
                    StudentListDTO dto = new StudentListDTO();
                    dto.setId(student.getId());
                    dto.setUsername(student.getUser().getUsername());
                    dto.setAccountStatus(student.getUser().isEnabled() ? "ENABLED" : "DISABLED");

                    dto.setFirstNameAMH(student.getFirstNameAMH());
                    dto.setFirstNameENG(student.getFirstNameENG());
                    dto.setFatherNameAMH(student.getFatherNameAMH());
                    dto.setFatherNameENG(student.getFatherNameENG());
                    dto.setGrandfatherNameAMH(student.getGrandfatherNameAMH());
                    dto.setGrandfatherNameENG(student.getGrandfatherNameENG());

                    dto.setStudentRecentStatus(student.getStudentRecentStatus().getStatusName());
                    dto.setDepartmentEnrolled(student.getDepartmentEnrolled().getDeptName());
                    dto.setBatchId(student.getBatch() != null ? student.getBatch().getId() : null);
                    dto.setBatchName(student.getBatch() != null ? student.getBatch().getBatchName() : null);
                    dto.setBatchClassYearSemester(student.getBatchClassYearSemester().getDisplayName());

                    dto.setStudentPhoto(student.getStudentPhoto());

                    return dto;
                })
                .collect(Collectors.toList());
    }

    // Retrieves a student by ID as a DTO, ensuring they are active
    // Why: Returns DTO for detailed views, includes all fields, respects enabled flag
    public StudentDetailsDTO getStudentById(Long id) {
        try {
            StudentDetails student = studentDetailsRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
            return mapToDTO(student);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve student with id " + id + ": " + e.getMessage());
        }
    }

    public List<StudentsListForSlipDTO> getStudentsForSlipProduction() {
        return studentDetailsRepository.findAll().stream()
                .map(student -> {
                    User user = student.getUser();
                    Department dept = student.getDepartmentEnrolled(); // or student.getDepartmentEnrolled()
                    BatchClassYearSemester bcys = student.getBatchClassYearSemester();

                    ProgramModality modality = dept.getProgramModality();
                    ProgramLevel level = modality != null ? modality.getProgramLevel() : null;

                    StudentsListForSlipDTO dto = new StudentsListForSlipDTO();
                    dto.setStudentId(student.getId());
                    dto.setUsername(user.getUsername());

                    // Full Name AMH & ENG
                    dto.setFullNameAMH(
                            String.join(" ",
                                    student.getFirstNameAMH(),
                                    student.getFatherNameAMH(),
                                    student.getGrandfatherNameAMH()).trim()
                    );
                    dto.setFullNameENG(
                            String.join(" ",
                                    student.getFirstNameENG(),
                                    student.getFatherNameENG(),
                                    student.getGrandfatherNameENG()).trim()
                    );

                    // BCYS
                    dto.setBcysId(bcys.getBcysID());
                    dto.setBcysDisplayName(bcys.getDisplayName());

                    // Department
                    dto.setDepartmentId(dept.getDptID());
                    dto.setDepartmentName(dept.getDeptName());

                    // Program Modality
                    if (modality != null) {
                        dto.setProgramModalityCode(modality.getModalityCode());
                        dto.setProgramModalityName(modality.getModality());
                    }

                    // Program Level
                    if (level != null) {
                        dto.setProgramLevelCode(level.getCode());
                        dto.setProgramLevelName(level.getName());
                    }

                    return dto;
                })
                .sorted(Comparator.comparing(StudentsListForSlipDTO::getFullNameENG))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all students with their Cumulative GPA (CGPA).
     * Why: For Dean, ViceDean, and General Manager to view student performance.
     */
    @Transactional(readOnly = true)
    public List<StudentCGPADTO> getAllStudentsWithCGPA() {
        return studentDetailsRepository.findAll().stream()
                .map(student -> {
                    StudentCGPADTO dto = new StudentCGPADTO();

                    dto.setStudentId(student.getId());
                    dto.setStudentUserId(student.getUser().getId());
                    dto.setIdNumber(student.getUser().getUsername());
                    
                    String fullName = String.join(" ",
                            student.getFirstNameENG(),
                            student.getFatherNameENG(),
                            student.getGrandfatherNameENG()).trim();
                    dto.setFullName(fullName);

                    dto.setDepartment(student.getDepartmentEnrolled().getDeptName());
                    dto.setBatchId(student.getBatch() != null ? student.getBatch().getId() : null);
                    dto.setBatchName(student.getBatch() != null ? student.getBatch().getBatchName() : null);
                    dto.setBatchClassYearSemester(student.getBatchClassYearSemester().getDisplayName());
                    dto.setStudentStatus(student.getStudentRecentStatus().getStatusName());

                    // Find CGPA and Total Credits Taken
                    BigDecimal storedCgpa = student.getCgpa();
                    double roundedCgpa = (storedCgpa != null)
                            ? storedCgpa.setScale(2, RoundingMode.HALF_UP).doubleValue()
                            : 0.0;

                    dto.setCgpa(roundedCgpa);
                    int totalCredits = (student.getTotalEarnedCreditHours() != null
                            ? student.getTotalEarnedCreditHours()
                            : 0);
                    dto.setCompletedCredits(totalCredits);

                    return dto;
                })
                .collect(Collectors.toList());
    }

    // Returns a complete student profile DTO for the given authenticated user
    // Why: Prevents direct exposure of StudentDetails entity, allows selective field inclusion
    // Throws ResourceNotFoundException if no StudentDetails exists for this user
    @Transactional(readOnly = true)
    public StudentProfileResponse getStudentProfileByUser(User user) {
        // Fetch the linked StudentDetails - throw if not found
        StudentDetails details = studentDetailsRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user: " + user.getUsername()));

        // Build full names
        String fullNameEnglish = String.join(" ",
                details.getFirstNameENG(),
                details.getFatherNameENG(),
                details.getGrandfatherNameENG()).trim();

        String fullNameAmharic = String.join(" ",
                details.getFirstNameAMH(),
                details.getFatherNameAMH(),
                details.getGrandfatherNameAMH()).trim();

        // Contact full name is stored in contactPersonFirstNameENG.
        String contactFullNameENG = details.getContactPersonFirstNameENG();

        return StudentProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullNameEnglish(fullNameEnglish)
                .fullNameAmharic(fullNameAmharic)
                .gender(details.getGender())                          // enum - kept as is
                .age(details.getAge())
                .phoneNumber(details.getPhoneNumber())
                .email(details.getEmail())
                .dateOfBirthGC(details.getDateOfBirthGC())
                .dateOfBirthEC(details.getDateOfBirthEC())
                .maritalStatus(details.getMaritalStatus())            // enum - kept as is
                .photoBase64(details.getStudentPhoto() != null
                        ? Base64.getEncoder().encodeToString(details.getStudentPhoto())
                        : null)
                // Address info (nested directly inside DTO)
            .placeOfBirthRegion(details.getPlaceOfBirthRegion() != null ? details.getPlaceOfBirthRegion().getRegion() : null)
            .placeOfBirthZone(details.getPlaceOfBirthZone() != null ? details.getPlaceOfBirthZone().getZone() : null)
            .placeOfBirthWoreda(details.getPlaceOfBirthWoreda() != null ? details.getPlaceOfBirthWoreda().getWoreda() : null)
            .currentAddressRegion(details.getCurrentAddressRegion() != null ? details.getCurrentAddressRegion().getRegion() : null)
            .currentAddressZone(details.getCurrentAddressZone() != null ? details.getCurrentAddressZone().getZone() : null)
            .currentAddressWoreda(details.getCurrentAddressWoreda() != null ? details.getCurrentAddressWoreda().getWoreda() : null)
                // Only names for related entities
                .impairment(details.getImpairment() != null ? details.getImpairment().getImpairment() : null)
            .schoolBackground(details.getSchoolBackground() != null ? details.getSchoolBackground().getBackground() : null)
                .contactPersonFullNameENG(contactFullNameENG)
                .contactPersonPhoneNumber(details.getContactPersonPhoneNumber())
                .contactPersonRelation(details.getContactPersonRelation())
                .dateEnrolledGC(details.getDateEnrolledGC())
                .academicYear(details.getAcademicYear() != null ? details.getAcademicYear().getYearCode() : null)
                .batchId(details.getBatch() != null ? details.getBatch().getId() : null)
                .batchName(details.getBatch() != null ? details.getBatch().getBatchName() : null)
            .batchClassYearSemester(details.getBatchClassYearSemester() != null ? details.getBatchClassYearSemester().getDisplayName() : null)
            .studentRecentStatus(details.getStudentRecentStatus() != null ? details.getStudentRecentStatus().getStatusName() : null)
            .departmentEnrolled(details.getDepartmentEnrolled() != null ? details.getDepartmentEnrolled().getDeptName() : null)
            .programModality(details.getProgramModality() != null ? details.getProgramModality().getModality() : null)
                .documentStatus(details.getDocumentStatus())          // enum - kept as is
                .grade12Result(details.getGrade12Result())
                .isStudentPassExitExam(details.getIsStudentPassExitExam())
                .yearOfExamG12(details.getYearOfExamG12())
                .nationalexamIdG12(details.getNationalexamIdG12())
                .dateClassEndGC(details.getDateClassEndGC())
                .dateGraduated(details.getDateGraduated())
                .entryYearGC(details.getEntryYearGC())
                .entryYearEC(details.getEntryYearEC())
                .build();
    }


    // Updates a student's details with optional file uploads
    // Why: Allows modification with multipart form data, respects enabled status
    // Updates a student's details with optional file uploads and returns DTO
    // Why: Allows modification with multipart form data, returns updated DTO
    @Transactional(rollbackFor = Exception.class)
    public StudentDetailsDTO updateStudent(Long id, StudentUpdateDTO dto, MultipartFile studentPhoto, MultipartFile document) {
        // Validate DTO
        if (dto == null) {
            throw new IllegalArgumentException("Update request cannot be null");
        }

        // Fetch existing student
        StudentDetails existing;
        try {
            existing = studentDetailsRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch student for update: " + e.getMessage());
        }

        // Check for duplicate phone number
        if (dto.getPhoneNumber() != null && !dto.getPhoneNumber().isEmpty()
                && !dto.getPhoneNumber().equals(existing.getPhoneNumber())
                && studentDetailsRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
            throw new IllegalArgumentException("Phone number already in use");
        }
        // Check for duplicate exitExamUserID if provided
        if (dto.getExitExamUserID() != null && !dto.getExitExamUserID().isEmpty()
                && !dto.getExitExamUserID().equals(existing.getExitExamUserID())
                && studentDetailsRepository.existsByExitExamUserID(dto.getExitExamUserID())) {
            throw new IllegalArgumentException("Exit exam user ID already in use");
        }

        // Validate file sizes
        if (studentPhoto != null && !studentPhoto.isEmpty() && studentPhoto.getSize() > 2_000_000) { // 2MB limit
            throw new IllegalArgumentException("Student photo size exceeds 2MB limit");
        }
        if (document != null && !document.isEmpty() && document.getSize() > 10_000_000) { // 10MB limit
            throw new IllegalArgumentException("Document size exceeds 10MB limit");
        }

        // Update fields if provided
        try {
            updateStudentFields(existing, dto, studentPhoto, document);
            StudentDetails updated = studentDetailsRepository.save(existing);
            return mapToDTO(updated);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Failed to update student due to duplicate entry or constraint violation: " + e.getMessage());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to process file uploads: " + e.getMessage());
        }
    }

    // Enables a student by enabling their user account
    // Why: Activates account for login, sets all flags to active
    public void enableStudent(Long studentId) {
        try {
            StudentDetails student = studentDetailsRepository.findById(studentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentId));
            userService.enableUser(student.getUser().getId());
            notificationService.createNotification(Arrays.asList(
                            Role.GENERAL_MANAGER, Role.DEAN, Role.VICE_DEAN, Role.DEPARTMENT_HEAD, Role.FINANCIAL_STAFF),
                    null, Role.REGISTRAR,
                    "Student " + student.getUser().getUsername() + " Can access his Account");
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to enable student with id " + studentId + ": " + e.getMessage());
        }
    }

    // Disables a student by disabling their user account
    // Why: Suspends account without deletion, respects dependencies
    public void disableStudent(Long studentId) {
        try {
            StudentDetails student = studentDetailsRepository.findById(studentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentId));
            userService.disableUser(student.getUser().getId());
            notificationService.createNotification(Arrays.asList(
                            Role.GENERAL_MANAGER, Role.DEAN, Role.VICE_DEAN, Role.DEPARTMENT_HEAD, Role.FINANCIAL_STAFF),
                    null, Role.REGISTRAR,
                    "Student " + student.getUser().getUsername() + " Cannot access his Account any more");
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to disable student with id " + studentId + ": " + e.getMessage());
        }
    }

    // Validates registration request fields
    // Why: Ensures all required fields are present and valid before processing
    private void validateRegistrationRequest(StudentRegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (request.getFirstNameAMH() == null || request.getFirstNameAMH().isEmpty()) {
            throw new IllegalArgumentException("First name (Amharic) cannot be empty");
        }
        if (request.getFirstNameENG() == null || request.getFirstNameENG().isEmpty()) {
            throw new IllegalArgumentException("First name (English) cannot be empty");
        }
        if (request.getFatherNameAMH() == null || request.getFatherNameAMH().isEmpty()) {
            throw new IllegalArgumentException("Father name (Amharic) cannot be empty");
        }
        if (request.getFatherNameENG() == null || request.getFatherNameENG().isEmpty()) {
            throw new IllegalArgumentException("Father name (English) cannot be empty");
        }
        if (request.getGrandfatherNameAMH() == null || request.getGrandfatherNameAMH().isEmpty()) {
            throw new IllegalArgumentException("Grandfather name (Amharic) cannot be empty");
        }
        if (request.getGrandfatherNameENG() == null || request.getGrandfatherNameENG().isEmpty()) {
            throw new IllegalArgumentException("Grandfather name (English) cannot be empty");
        }
        if (request.getMotherNameAMH() == null || request.getMotherNameAMH().isEmpty()) {
            throw new IllegalArgumentException("Mother name (Amharic) cannot be empty");
        }
        if (request.getMotherNameENG() == null || request.getMotherNameENG().isEmpty()) {
            throw new IllegalArgumentException("Mother name (English) cannot be empty");
        }
        if (request.getGender() == null) {
            throw new IllegalArgumentException("Gender cannot be null");
        }
        if (request.getAge() != null && request.getAge() < 0) {
            throw new IllegalArgumentException("Age must be a non-negative integer");
        }
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be empty");
        }
//        if (request.getDateOfBirthEC() == null || request.getDateOfBirthEC().isEmpty()) {
//            throw new IllegalArgumentException("Date of birth (EC) cannot be empty");
//        }
        if (request.getDateOfBirthGC() == null) {
            throw new IllegalArgumentException("Date of birth (GC) cannot be null");
        }
        if (request.getPlaceOfBirthWoredaCode() == null) {
            throw new IllegalArgumentException("Place of birth Woreda code cannot be null");
        }
        if (request.getPlaceOfBirthZoneCode() == null) {
            throw new IllegalArgumentException("Place of birth Zone code cannot be null");
        }
        if (request.getPlaceOfBirthRegionCode() == null) {
            throw new IllegalArgumentException("Place of birth Region code cannot be null");
        }
        if (request.getCurrentAddressWoredaCode() == null) {
            throw new IllegalArgumentException("Current address Woreda code cannot be null");
        }
        if (request.getCurrentAddressZoneCode() == null) {
            throw new IllegalArgumentException("Current address Zone code cannot be null");
        }
        if (request.getCurrentAddressRegionCode() == null) {
            throw new IllegalArgumentException("Current address Region code cannot be null");
        }
        if (request.getMaritalStatus() == null) {
            throw new IllegalArgumentException("Marital status cannot be null");
        }
        if (request.getSchoolBackgroundId() == null) {
            throw new IllegalArgumentException("School background cannot be null");
        }
        if (request.getContactPersonFirstNameAMH() == null || request.getContactPersonFirstNameAMH().isEmpty()) {
            throw new IllegalArgumentException("Contact person first name (Amharic) cannot be empty");
        }
        if (request.getContactPersonFirstNameENG() == null || request.getContactPersonFirstNameENG().isEmpty()) {
            throw new IllegalArgumentException("Contact person first name (English) cannot be empty");
        }
        if (request.getContactPersonPhoneNumber() == null || request.getContactPersonPhoneNumber().isEmpty()) {
            throw new IllegalArgumentException("Contact person phone number cannot be empty");
        }
//        if (request.getDateEnrolledEC() == null || request.getDateEnrolledEC().isEmpty()) {
//            throw new IllegalArgumentException("Date enrolled (EC) cannot be empty");
//        }
        if (request.getDateEnrolledGC() == null) {
            throw new IllegalArgumentException("Date enrolled (GC) cannot be null");
        }
//        if (request.getAcademicYearCode() == null || request.getAcademicYearCode().isEmpty()) {
//            throw new IllegalArgumentException("Academic year code cannot be null or empty");
//        }
        if (request.getBatchClassYearSemesterId() == null) {
            throw new IllegalArgumentException("Batch class year semester cannot be null");
        }
        if (request.getStudentRecentStatusId() == null) {
            throw new IllegalArgumentException("Student recent status cannot be null");
        }
        if (request.getDepartmentEnrolledId() == null) {
            throw new IllegalArgumentException("Department enrolled cannot be null");
        }
        if (request.getBatchId() != null && !batchRepository.existsById(request.getBatchId())) {
            throw new IllegalArgumentException("Batch not found with id: " + request.getBatchId());
        }
        if (request.getProgramModalityCode() == null) {
            throw new IllegalArgumentException("Program modality cannot be null");
        }
    }

    // Updates student fields from DTO and file uploads
    // Why: Centralizes update logic, only updates provided fields and files
    private void updateStudentFields(StudentDetails student, StudentUpdateDTO dto, MultipartFile studentPhoto, MultipartFile document) throws IOException {
        if (dto.getFirstNameAMH() != null && !dto.getFirstNameAMH().isEmpty()) {
            student.setFirstNameAMH(dto.getFirstNameAMH());
        }
        if (dto.getFirstNameENG() != null && !dto.getFirstNameENG().isEmpty()) {
            student.setFirstNameENG(dto.getFirstNameENG());
        }
        if (dto.getFatherNameAMH() != null && !dto.getFatherNameAMH().isEmpty()) {
            student.setFatherNameAMH(dto.getFatherNameAMH());
        }
        if (dto.getFatherNameENG() != null && !dto.getFatherNameENG().isEmpty()) {
            student.setFatherNameENG(dto.getFatherNameENG());
        }
        if (dto.getGrandfatherNameAMH() != null && !dto.getGrandfatherNameAMH().isEmpty()) {
            student.setGrandfatherNameAMH(dto.getGrandfatherNameAMH());
        }
        if (dto.getGrandfatherNameENG() != null && !dto.getGrandfatherNameENG().isEmpty()) {
            student.setGrandfatherNameENG(dto.getGrandfatherNameENG());
        }
        if (dto.getMotherNameAMH() != null && !dto.getMotherNameAMH().isEmpty()) {
            student.setMotherNameAMH(dto.getMotherNameAMH());
        }
        if (dto.getMotherNameENG() != null && !dto.getMotherNameENG().isEmpty()) {
            student.setMotherNameENG(dto.getMotherNameENG());
        }
        if (dto.getMotherFatherNameAMH() != null && !dto.getMotherFatherNameAMH().isEmpty()) {
            student.setMotherFatherNameAMH(dto.getMotherFatherNameAMH());
        }
        if (dto.getMotherFatherNameENG() != null && !dto.getMotherFatherNameENG().isEmpty()) {
            student.setMotherFatherNameENG(dto.getMotherFatherNameENG());
        }
        if (dto.getGender() != null) {
            student.setGender(dto.getGender());
        }
        if (dto.getAge() != null && dto.getAge() >= 0) {
            student.setAge(dto.getAge());
        }
        if (dto.getPhoneNumber() != null && !dto.getPhoneNumber().isEmpty()) {
            student.setPhoneNumber(dto.getPhoneNumber());
        }
        if (dto.getDateOfBirthEC() != null && !dto.getDateOfBirthEC().isEmpty()) {
            student.setDateOfBirthEC(dto.getDateOfBirthEC());
        }
        if (dto.getDateOfBirthGC() != null) {
            student.setDateOfBirthGC(dto.getDateOfBirthGC());
        }
        if (dto.getPlaceOfBirthWoredaCode() != null) {
            Woreda woreda = woredaRepository.findById(dto.getPlaceOfBirthWoredaCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Woreda not found with code: " + dto.getPlaceOfBirthWoredaCode()));
            student.setPlaceOfBirthWoreda(woreda);
        }
        if (dto.getPlaceOfBirthZoneCode() != null) {
            Zone zone = zoneRepository.findById(dto.getPlaceOfBirthZoneCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Zone not found with code: " + dto.getPlaceOfBirthZoneCode()));
            student.setPlaceOfBirthZone(zone);
        }
        if (dto.getPlaceOfBirthRegionCode() != null) {
            Region region = regionRepository.findById(dto.getPlaceOfBirthRegionCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Region not found with code: " + dto.getPlaceOfBirthRegionCode()));
            student.setPlaceOfBirthRegion(region);
        }
        if (dto.getCurrentAddressWoredaCode() != null) {
            Woreda woreda = woredaRepository.findById(dto.getCurrentAddressWoredaCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Woreda not found with code: " + dto.getCurrentAddressWoredaCode()));
            student.setCurrentAddressWoreda(woreda);
        }
        if (dto.getCurrentAddressZoneCode() != null) {
            Zone zone = zoneRepository.findById(dto.getCurrentAddressZoneCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Zone not found with code: " + dto.getCurrentAddressZoneCode()));
            student.setCurrentAddressZone(zone);
        }
        if (dto.getCurrentAddressRegionCode() != null) {
            Region region = regionRepository.findById(dto.getCurrentAddressRegionCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Region not found with code: " + dto.getCurrentAddressRegionCode()));
            student.setCurrentAddressRegion(region);
        }
        if (dto.getEmail() != null) {
            student.setEmail(dto.getEmail());
        }
        if (dto.getMaritalStatus() != null) {
            student.setMaritalStatus(dto.getMaritalStatus());
        }
        if (dto.getImpairmentCode() != null) {
            Impairment impairment = impairmentRepository.findById(dto.getImpairmentCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Impairment not found with code: " + dto.getImpairmentCode()));
            student.setImpairment(impairment);
        }


        if (dto.getSchoolBackgroundId() != null) {
            SchoolBackground background = schoolBackgroundRepository.findById(dto.getSchoolBackgroundId())
                    .orElseThrow(() -> new ResourceNotFoundException("School background not found with id: " + dto.getSchoolBackgroundId()));
            student.setSchoolBackground(background);
        }
        if (studentPhoto != null && !studentPhoto.isEmpty()) {
            student.setStudentPhoto(studentPhoto.getBytes());
        }
        if (dto.getContactPersonFirstNameAMH() != null && !dto.getContactPersonFirstNameAMH().isEmpty()) {
            student.setContactPersonFirstNameAMH(dto.getContactPersonFirstNameAMH());
        }
        if (dto.getContactPersonFirstNameENG() != null && !dto.getContactPersonFirstNameENG().isEmpty()) {
            student.setContactPersonFirstNameENG(dto.getContactPersonFirstNameENG());
        }
        if (dto.getContactPersonLastNameAMH() != null && !dto.getContactPersonLastNameAMH().isEmpty()) {
            student.setContactPersonLastNameAMH(dto.getContactPersonLastNameAMH());
        }
        if (dto.getContactPersonLastNameENG() != null && !dto.getContactPersonLastNameENG().isEmpty()) {
            student.setContactPersonLastNameENG(dto.getContactPersonLastNameENG());
        }
        if (dto.getContactPersonFullNameENG() != null && !dto.getContactPersonFullNameENG().isEmpty()) {
            student.setContactPersonFirstNameENG(dto.getContactPersonFullNameENG());
            student.setContactPersonLastNameENG(null);
        } else if ((dto.getContactPersonFirstNameENG() != null && !dto.getContactPersonFirstNameENG().isEmpty())
                || (dto.getContactPersonLastNameENG() != null && !dto.getContactPersonLastNameENG().isEmpty())) {
            student.setContactPersonFirstNameENG(buildFullName(dto.getContactPersonFirstNameENG(), dto.getContactPersonLastNameENG()));
            student.setContactPersonLastNameENG(null);
        }
        if (dto.getContactPersonPhoneNumber() != null && !dto.getContactPersonPhoneNumber().isEmpty()) {
            student.setContactPersonPhoneNumber(dto.getContactPersonPhoneNumber());
        }
        if (dto.getContactPersonRelation() != null) {
            student.setContactPersonRelation(dto.getContactPersonRelation());
        }
        if (dto.getDateEnrolledEC() != null && !dto.getDateEnrolledEC().isEmpty()) {
            student.setDateEnrolledEC(dto.getDateEnrolledEC());
        }
        if (dto.getDateEnrolledGC() != null) {
            student.setDateEnrolledGC(dto.getDateEnrolledGC());
        }

        if (dto.getAcademicYearCode() != null && !dto.getAcademicYearCode().isEmpty()) {
            AcademicYear academicYear = academicYearRepository.findById(dto.getAcademicYearCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Academic year not found with code: " + dto.getAcademicYearCode()));
            student.setAcademicYear(academicYear);
        }
        if (dto.getBatchClassYearSemesterId() != null) {
            BatchClassYearSemester bcys = batchClassYearSemesterRepository.findById(dto.getBatchClassYearSemesterId())
                    .orElseThrow(() -> new ResourceNotFoundException("BatchClassYearSemester not found with id: " + dto.getBatchClassYearSemesterId()));
            student.setBatchClassYearSemester(bcys);
        }
        if (dto.getStudentRecentStatusId() != null) {
            StudentStatus status = studentStatusRepository.findById(dto.getStudentRecentStatusId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student status not found with id: " + dto.getStudentRecentStatusId()));
            student.setStudentRecentStatus(status);
        }
        if (dto.getDepartmentEnrolledId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartmentEnrolledId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + dto.getDepartmentEnrolledId()));
            student.setDepartmentEnrolled(dept);
        }
        if (dto.getBatchId() != null) {
            Batch batch = batchRepository.findById(dto.getBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + dto.getBatchId()));
            student.setBatch(batch);
        }
        if (dto.getProgramModalityCode() != null) {
            ProgramModality modality = programModalityRepository.findById(dto.getProgramModalityCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Program modality not found with code: " + dto.getProgramModalityCode()));
            student.setProgramModality(modality);
        }
        if (document != null && !document.isEmpty()) {
            student.setDocument(document.getBytes());
        }
        if (dto.getDocumentStatus() != null) {
            student.setDocumentStatus(dto.getDocumentStatus());
        }
        if (dto.getRemark() != null) {
            student.setRemark(dto.getRemark());
        }
        if (dto.getIsTransfer() != null) {
            student.setTransfer(dto.getIsTransfer());
        }
        if (dto.getExitExamUserID() != null) {
            student.setExitExamUserID(dto.getExitExamUserID());
        }
        if (dto.getExitExamScore() != null) {
            student.setExitExamScore(dto.getExitExamScore());
        }
        if (dto.getIsStudentPassExitExam() != null) {
            student.setStudentPassExitExam(dto.getIsStudentPassExitExam());
        }
        if (dto.getGrade12Result() != null) {
            student.setGrade12Result(dto.getGrade12Result());
        }
        if (dto.getYearOfExamG12() != null) {
            student.setYearOfExamG12(dto.getYearOfExamG12());
        }
        if (dto.getNationalexamIdG12() != null) {
            student.setNationalexamIdG12(dto.getNationalexamIdG12());
        }
        if (dto.getDateClassEndGC() != null) {
            student.setDateClassEndGC(dto.getDateClassEndGC());
        }
        if (dto.getDateGraduated() != null) {
            student.setDateGraduated(dto.getDateGraduated());
        }
        if (dto.getEntryYearGC() != null) {
            student.setEntryYearGC(dto.getEntryYearGC());
        }
        if (dto.getEntryYearEC() != null) {
            student.setEntryYearEC(dto.getEntryYearEC());
        }
    }

    // Maps registration request to StudentDetails entity with file uploads
    // Why: Centralizes mapping logic, handles file conversion and relationships
    private StudentDetails mapRequestToEntity(StudentRegisterRequest request, User user, MultipartFile studentPhoto, MultipartFile document) throws IOException {
        StudentDetails student = new StudentDetails();
        student.setUser(user);
        student.setFirstNameAMH(request.getFirstNameAMH());
        student.setFirstNameENG(request.getFirstNameENG());
        student.setFatherNameAMH(request.getFatherNameAMH());
        student.setFatherNameENG(request.getFatherNameENG());
        student.setGrandfatherNameAMH(request.getGrandfatherNameAMH());
        student.setGrandfatherNameENG(request.getGrandfatherNameENG());
        student.setMotherNameAMH(request.getMotherNameAMH());
        student.setMotherNameENG(request.getMotherNameENG());
        student.setMotherFatherNameAMH(request.getMotherFatherNameAMH());
        student.setMotherFatherNameENG(request.getMotherFatherNameENG());
        student.setGender(request.getGender());
        student.setAge(request.getAge());
        student.setPhoneNumber(request.getPhoneNumber());
        student.setDateOfBirthEC(request.getDateOfBirthEC());
        student.setDateOfBirthGC(request.getDateOfBirthGC());
        student.setPlaceOfBirthWoreda(woredaRepository.findById(request.getPlaceOfBirthWoredaCode())
                .orElseThrow(() -> new ResourceNotFoundException("Woreda not found with code: " + request.getPlaceOfBirthWoredaCode())));
        student.setPlaceOfBirthZone(zoneRepository.findById(request.getPlaceOfBirthZoneCode())
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with code: " + request.getPlaceOfBirthZoneCode())));
        student.setPlaceOfBirthRegion(regionRepository.findById(request.getPlaceOfBirthRegionCode())
                .orElseThrow(() -> new ResourceNotFoundException("Region not found with code: " + request.getPlaceOfBirthRegionCode())));
        student.setCurrentAddressWoreda(woredaRepository.findById(request.getCurrentAddressWoredaCode())
                .orElseThrow(() -> new ResourceNotFoundException("Woreda not found with code: " + request.getCurrentAddressWoredaCode())));
        student.setCurrentAddressZone(zoneRepository.findById(request.getCurrentAddressZoneCode())
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with code: " + request.getCurrentAddressZoneCode())));
        student.setCurrentAddressRegion(regionRepository.findById(request.getCurrentAddressRegionCode())
                .orElseThrow(() -> new ResourceNotFoundException("Region not found with code: " + request.getCurrentAddressRegionCode())));
        student.setEmail(request.getEmail());
        student.setMaritalStatus(request.getMaritalStatus());
        if (request.getImpairmentCode() != null && !request.getImpairmentCode().isEmpty()) {
            student.setImpairment(impairmentRepository.findById(request.getImpairmentCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Impairment not found with code: " + request.getImpairmentCode())));
        }
        if (request.getAcademicYearCode() != null && !request.getAcademicYearCode().isEmpty()) {
            student.setAcademicYear(academicYearRepository.findById(request.getAcademicYearCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Academic year not found with code: " + request.getAcademicYearCode())));
        }
        student.setSchoolBackground(schoolBackgroundRepository.findById(request.getSchoolBackgroundId())
                .orElseThrow(() -> new ResourceNotFoundException("School background not found with id: " + request.getSchoolBackgroundId())));
        if (studentPhoto != null && !studentPhoto.isEmpty()) {
            student.setStudentPhoto(studentPhoto.getBytes());
        }
        student.setContactPersonFirstNameAMH(request.getContactPersonFirstNameAMH());
        student.setContactPersonFirstNameENG(buildFullName(request.getContactPersonFirstNameENG(), request.getContactPersonLastNameENG()));
        student.setContactPersonLastNameAMH(request.getContactPersonLastNameAMH());
        student.setContactPersonLastNameENG(null);
        student.setContactPersonPhoneNumber(request.getContactPersonPhoneNumber());
        student.setContactPersonRelation(request.getContactPersonRelation());
        student.setDateEnrolledEC(request.getDateEnrolledEC());
        student.setDateEnrolledGC(request.getDateEnrolledGC());
        BatchClassYearSemester bcys = batchClassYearSemesterRepository.findById(request.getBatchClassYearSemesterId())
                .orElseThrow(() -> new ResourceNotFoundException("BatchClassYearSemester not found with id: " + request.getBatchClassYearSemesterId()));
        student.setBatchClassYearSemester(bcys);
//        student.setStudentRecentBatch(bcys);
        student.setStudentRecentStatus(studentStatusRepository.findById(request.getStudentRecentStatusId())
                .orElseThrow(() -> new ResourceNotFoundException("Student status not found with id: " + request.getStudentRecentStatusId())));
        Department dept = departmentRepository.findById(request.getDepartmentEnrolledId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentEnrolledId()));
        student.setDepartmentEnrolled(dept);
        if (request.getBatchId() != null) {
            student.setBatch(batchRepository.findById(request.getBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + request.getBatchId())));
        }
//        student.setStudentRecentDepartment(dept);
        student.setProgramModality(programModalityRepository.findById(request.getProgramModalityCode())
                .orElseThrow(() -> new ResourceNotFoundException("Program modality not found with code: " + request.getProgramModalityCode())));
        if (document != null && !document.isEmpty()) {
            student.setDocument(document.getBytes());
        }
        student.setDocumentStatus(request.getDocumentStatus() != null ? request.getDocumentStatus() : DocumentStatus.INCOMPLETE);
        student.setRemark(request.getRemark());
        student.setTransfer(request.getIsTransfer() != null ? request.getIsTransfer() : false);
        student.setExitExamUserID(request.getExitExamUserID());
        student.setExitExamScore(request.getExitExamScore());
        student.setStudentPassExitExam(request.getIsStudentPassExitExam() != null
            ? request.getIsStudentPassExitExam()
            : ExitExamPassStatus.Not_Taken);
        student.setGrade12Result(request.getGrade12Result());
        return student;
    }

    // Maps StudentDetails entity to StudentDetailsDTO
// Why: Converts entity to DTO to include all fields safely, with null checks for nullable relationships
    private StudentDetailsDTO mapToDTO(StudentDetails student) {
        StudentDetailsDTO dto = new StudentDetailsDTO();

        dto.setId(student.getId());
        dto.setUserId(student.getUser().getId());
        dto.setUsername(student.getUser().getUsername());

        // Personal Info
        dto.setFirstNameAMH(student.getFirstNameAMH());
        dto.setFirstNameENG(student.getFirstNameENG());
        dto.setFatherNameAMH(student.getFatherNameAMH());
        dto.setFatherNameENG(student.getFatherNameENG());
        dto.setGrandfatherNameAMH(student.getGrandfatherNameAMH());
        dto.setGrandfatherNameENG(student.getGrandfatherNameENG());
        dto.setMotherNameAMH(student.getMotherNameAMH());
        dto.setMotherNameENG(student.getMotherNameENG());
        dto.setMotherFatherNameAMH(student.getMotherFatherNameAMH());
        dto.setMotherFatherNameENG(student.getMotherFatherNameENG());

        dto.setGender(student.getGender().name());
        dto.setAge(student.getAge());
        dto.setPhoneNumber(student.getPhoneNumber());
        dto.setDateOfBirthEC(student.getDateOfBirthEC());
        dto.setDateOfBirthGC(student.getDateOfBirthGC());

        // Place of Birth - ID + Name (null-safe)
        dto.setPlaceOfBirthWoredaCode(student.getPlaceOfBirthWoreda() != null ? student.getPlaceOfBirthWoreda().getWoredaCode() : null);
        dto.setPlaceOfBirthWoredaName(student.getPlaceOfBirthWoreda() != null ? student.getPlaceOfBirthWoreda().getWoreda() : null);
        dto.setPlaceOfBirthZoneCode(student.getPlaceOfBirthZone() != null ? student.getPlaceOfBirthZone().getZoneCode() : null);
        dto.setPlaceOfBirthZoneName(student.getPlaceOfBirthZone() != null ? student.getPlaceOfBirthZone().getZone() : null);
        dto.setPlaceOfBirthRegionCode(student.getPlaceOfBirthRegion() != null ? student.getPlaceOfBirthRegion().getRegionCode() : null);
        dto.setPlaceOfBirthRegionName(student.getPlaceOfBirthRegion() != null ? student.getPlaceOfBirthRegion().getRegion() : null);

        // Current Address - ID + Name (null-safe)
        dto.setCurrentAddressWoredaCode(student.getCurrentAddressWoreda() != null ? student.getCurrentAddressWoreda().getWoredaCode() : null);
        dto.setCurrentAddressWoredaName(student.getCurrentAddressWoreda() != null ? student.getCurrentAddressWoreda().getWoreda() : null);
        dto.setCurrentAddressZoneCode(student.getCurrentAddressZone() != null ? student.getCurrentAddressZone().getZoneCode() : null);
        dto.setCurrentAddressZoneName(student.getCurrentAddressZone() != null ? student.getCurrentAddressZone().getZone() : null);
        dto.setCurrentAddressRegionCode(student.getCurrentAddressRegion() != null ? student.getCurrentAddressRegion().getRegionCode() : null);
        dto.setCurrentAddressRegionName(student.getCurrentAddressRegion() != null ? student.getCurrentAddressRegion().getRegion() : null);

        dto.setEmail(student.getEmail());
        dto.setMaritalStatus(student.getMaritalStatus().name());

        // Impairment (null-safe)
        dto.setImpairmentCode(student.getImpairment() != null ? student.getImpairment().getImpairmentCode() : null);
        dto.setImpairmentDescription(student.getImpairment() != null ? student.getImpairment().getImpairment() : null);

        // School Background (required, so safe)
        dto.setSchoolBackgroundId(student.getSchoolBackground().getId());
        dto.setSchoolBackgroundName(student.getSchoolBackground().getBackground());

        dto.setStudentPhoto(student.getStudentPhoto());

        // Emergency Contact
        dto.setContactPersonFirstNameAMH(student.getContactPersonFirstNameAMH());
        dto.setContactPersonFirstNameENG(student.getContactPersonFirstNameENG());
        dto.setContactPersonFullNameENG(student.getContactPersonFirstNameENG());
        dto.setContactPersonLastNameAMH(student.getContactPersonLastNameAMH());
        dto.setContactPersonLastNameENG(student.getContactPersonLastNameENG());
        dto.setContactPersonPhoneNumber(student.getContactPersonPhoneNumber());
        dto.setContactPersonRelation(student.getContactPersonRelation());

        // Academic Info
        dto.setDateEnrolledEC(student.getDateEnrolledEC());
        dto.setDateEnrolledGC(student.getDateEnrolledGC());

        // Batch Class Year Semester (required, safe)
        dto.setBatchClassYearSemesterId(student.getBatchClassYearSemester().getBcysID());
        dto.setBatchClassYearSemesterName(student.getBatchClassYearSemester().getDisplayName());

        // Student Recent Status (required, safe)
        dto.setStudentRecentStatusId(student.getStudentRecentStatus().getId());
        dto.setStudentRecentStatusName(student.getStudentRecentStatus().getStatusName());

        // Department Enrolled (required, safe)
        dto.setDepartmentEnrolledId(student.getDepartmentEnrolled().getDptID());
        dto.setDepartmentEnrolledName(student.getDepartmentEnrolled().getDeptName());

        dto.setBatchId(student.getBatch() != null ? student.getBatch().getId() : null);
        dto.setBatchName(student.getBatch() != null ? student.getBatch().getBatchName() : null);

        // Program Modality (required, safe)
        dto.setProgramModalityCode(student.getProgramModality().getModalityCode());
        dto.setProgramModalityName(student.getProgramModality().getModality());

        // Academic Year (null-safe)
        if (student.getAcademicYear() != null) {
            dto.setAcademicYearCode(student.getAcademicYear().getYearCode());
            dto.setAcademicYearGC(student.getAcademicYear().getAcademicYearGC());
            dto.setAcademicYearEC(student.getAcademicYear().getAcademicYearEC());
        }

        // Document & Others
        dto.setDocument(student.getDocument());
        dto.setDocumentStatus(student.getDocumentStatus().name());
        dto.setRemark(student.getRemark());
        dto.setIsTransfer(student.isTransfer());
        dto.setExitExamUserID(student.getExitExamUserID());
        dto.setExitExamScore(student.getExitExamScore());
        dto.setIsStudentPassExitExam(student.getIsStudentPassExitExam());
        dto.setGrade12Result(student.getGrade12Result());
        dto.setYearOfExamG12(student.getYearOfExamG12());
        dto.setNationalexamIdG12(student.getNationalexamIdG12());
        dto.setDateClassEndGC(student.getDateClassEndGC());
        dto.setDateGraduated(student.getDateGraduated());
        dto.setEntryYearGC(student.getEntryYearGC());
        dto.setEntryYearEC(student.getEntryYearEC());

        return dto;
    }


// Method to add inside your existing StudentService

@Transactional(rollbackFor = Exception.class)
public StudentDetails acceptAppliedStudent(
        Long appliedStudentId,
        AcceptApplicationRequest request,
        MultipartFile studentPhoto,
        MultipartFile document) throws IOException {

    if (request.getBatchId() == null) {
        throw new IllegalArgumentException("Batch ID is required");
    }
    if (!batchRepository.existsById(request.getBatchId())) {
        throw new ResourceNotFoundException("Batch not found with id: " + request.getBatchId());
    }

    // Fetch applied student
    AppliedStudent applied = appliedStudentRepository.findById(appliedStudentId)
            .orElseThrow(() -> new ResourceNotFoundException("Applied student not found with id: " + appliedStudentId));

    // Check If already Accepted
    if (applied.getApplicationStatus() == ApplicationStatus.ACCEPTED) {
        throw new IllegalStateException("Application has already been accepted for applied student with id: " + appliedStudentId);
    }
    // Prepare StudentRegisterRequest using existing DTO
    StudentRegisterRequest registerRequest = new StudentRegisterRequest();

    // User credentials (required from AcceptApplicationRequest)
    registerRequest.setUsername(request.getUsername());
    registerRequest.setPassword(request.getPassword());

    // Personal Information - copy from AppliedStudent
    registerRequest.setFirstNameAMH(applied.getFirstNameAMH());
    registerRequest.setFirstNameENG(applied.getFirstNameENG());
    registerRequest.setFatherNameAMH(applied.getFatherNameAMH());
    registerRequest.setFatherNameENG(applied.getFatherNameENG());
    registerRequest.setGrandfatherNameAMH(applied.getGrandfatherNameAMH());
    registerRequest.setGrandfatherNameENG(applied.getGrandfatherNameENG());
    registerRequest.setMotherNameAMH(applied.getMotherNameAMH());
    registerRequest.setMotherNameENG(applied.getMotherNameENG());
    registerRequest.setMotherFatherNameAMH(applied.getMotherFatherNameAMH());
    registerRequest.setMotherFatherNameENG(applied.getMotherFatherNameENG());

    // Demographic Information
    registerRequest.setGender(applied.getGender());
    registerRequest.setAge(applied.getAge());
    registerRequest.setPhoneNumber(applied.getPhoneNumber());

    // Date of Birth
    registerRequest.setDateOfBirthEC(applied.getDateOfBirthEC());
    registerRequest.setDateOfBirthGC(applied.getDateOfBirthGC());

    // Place of Birth (using codes from entities)
    registerRequest.setPlaceOfBirthWoredaCode(applied.getPlaceOfBirthWoreda().getWoredaCode());
    registerRequest.setPlaceOfBirthZoneCode(applied.getPlaceOfBirthZone().getZoneCode());
    registerRequest.setPlaceOfBirthRegionCode(applied.getPlaceOfBirthRegion().getRegionCode());

    // Current Address
    registerRequest.setCurrentAddressWoredaCode(applied.getCurrentAddressWoreda().getWoredaCode());
    registerRequest.setCurrentAddressZoneCode(applied.getCurrentAddressZone().getZoneCode());
    registerRequest.setCurrentAddressRegionCode(applied.getCurrentAddressRegion().getRegionCode());

    // Additional Personal Information
    registerRequest.setEmail(applied.getEmail());
    registerRequest.setMaritalStatus(applied.getMaritalStatus());
    registerRequest.setImpairmentCode(applied.getImpairment() != null ? applied.getImpairment().getImpairmentCode() : null);
    registerRequest.setSchoolBackgroundId(applied.getSchoolBackground().getId());

    // Emergency Contact
    registerRequest.setContactPersonFirstNameAMH(applied.getContactPersonFirstNameAMH());
    registerRequest.setContactPersonFirstNameENG(applied.getContactPersonFirstNameENG());
    registerRequest.setContactPersonLastNameAMH(applied.getContactPersonLastNameAMH());
    registerRequest.setContactPersonLastNameENG(applied.getContactPersonLastNameENG());
    registerRequest.setContactPersonPhoneNumber(applied.getContactPersonPhoneNumber());
    registerRequest.setContactPersonRelation(applied.getContactPersonRelation());

    // Academic Preferences from AppliedStudent
    registerRequest.setDepartmentEnrolledId(applied.getDepartmentEnrolled().getDptID());
    registerRequest.setProgramModalityCode(applied.getProgramModality().getModalityCode());

    // Acceptance-specific required fields from request
    registerRequest.setDateEnrolledEC(request.getDateEnrolledEC());
    registerRequest.setDateEnrolledGC(request.getDateEnrolledGC());
    registerRequest.setAcademicYearCode(request.getAcademicYearCode());
    registerRequest.setBatchId(request.getBatchId());
    registerRequest.setBatchClassYearSemesterId(request.getBatchClassYearSemesterId());
    registerRequest.setStudentRecentStatusId(request.getStudentRecentStatusId());

    // Transfer and Exit Exam
    registerRequest.setIsTransfer(request.getIsTransfer());
    registerRequest.setExitExamUserID(request.getExitExamUserID());
    registerRequest.setExitExamScore(request.getExitExamScore());
    registerRequest.setIsStudentPassExitExam(request.getIsStudentPassExitExam());
    registerRequest.setGrade12Result(request.getGrade12Result());

    // Document status and remark
    registerRequest.setDocumentStatus(applied.getDocument() != null || document != null ? DocumentStatus.COMPLETE : DocumentStatus.INCOMPLETE);
    registerRequest.setRemark(request.getRemark());

    // Reuse existing registerStudent method which handles:
    // - Validation
    // - User creation
    // - File processing
    // - StudentDetails creation
    // - Notifications
    MultipartFile photoToUse = studentPhoto != null ? studentPhoto : null;
    MultipartFile documentToUse = document != null ? document : null;
    StudentDetails st = new StudentDetails();
    st = registerStudent(registerRequest, photoToUse, documentToUse);
System.out.println("---------Finished Registering Applicant --------");

    // Update application status to ACCEPTED
    applied.setApplicationStatus(ApplicationStatus.ACCEPTED);
    System.out.println("Updated the application Status...");
    appliedStudentRepository.save(applied);
    System.out.println("Saved the status to the database");

    return st;
}

    // StudentDetailsService - update this method
    public List<String> getAllFields() {
        // Use the DTO class instead of the entity
        Field[] fields = StudentDetailsSummaryDTO.class.getDeclaredFields();

        return Arrays.stream(fields)
                .map(Field::getName)
                .filter(name -> !name.equals("user"))     // extra safety (optional)
                .sorted()                                 // nicer output
                .collect(Collectors.toList());
    }

    //=================================================================================================
    /**
     * Retrieves a comprehensive summary of all students in the system.
     * Excludes binary fields (photo/document) and maps foreign key entities to {id, name} format.
     * Includes the student's username from the linked User entity.
     *
     * @return List of StudentDetailsSummaryDTO containing all required fields
     * @throws ResourceNotFoundException if no students are found (optional – can be removed if empty list is acceptable)
     */
    public List<StudentDetailsSummaryDTO> getAllStudentsSummary() {
        List<StudentDetails> students = studentDetailsRepository.findAll();

        if (students.isEmpty()) {
            // You can choose to return empty list instead of throwing
            // throw new ResourceNotFoundException("No students found in the system");
            return Collections.emptyList();
        }

        return students.stream()
                .map(this::mapToSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Maps a StudentDetails entity to its summary DTO.
     * Handles all foreign key mappings safely with null checks.
     */
    private StudentDetailsSummaryDTO mapToSummaryDTO(StudentDetails sd) {
        StudentDetailsSummaryDTO dto = new StudentDetailsSummaryDTO();

        // Primary identifiers
        dto.setId(sd.getId());
        dto.setUsername(sd.getUser() != null ? sd.getUser().getUsername() : null);

        // Personal information
        dto.setFirstNameAMH(sd.getFirstNameAMH());
        dto.setFirstNameENG(sd.getFirstNameENG());
        dto.setFatherNameAMH(sd.getFatherNameAMH());
        dto.setFatherNameENG(sd.getFatherNameENG());
        dto.setGrandfatherNameAMH(sd.getGrandfatherNameAMH());
        dto.setGrandfatherNameENG(sd.getGrandfatherNameENG());
        dto.setMotherNameAMH(sd.getMotherNameAMH());
        dto.setMotherNameENG(sd.getMotherNameENG());
        dto.setMotherFatherNameAMH(sd.getMotherFatherNameAMH());
        dto.setMotherFatherNameENG(sd.getMotherFatherNameENG());

        dto.setGender(sd.getGender());
        dto.setAge(sd.getAge());
        dto.setPhoneNumber(sd.getPhoneNumber());
        dto.setDateOfBirthEC(sd.getDateOfBirthEC());
        dto.setDateOfBirthGC(sd.getDateOfBirthGC());

        // Place of birth (safe null handling)
        dto.setPlaceOfBirthWoreda(toIdNameMap(sd.getPlaceOfBirthWoreda(),
                w -> w.getWoredaCode(), w -> w.getWoreda()));
        dto.setPlaceOfBirthZone(toIdNameMap(sd.getPlaceOfBirthZone(),
                z -> z.getZoneCode(), z -> z.getZone()));
        dto.setPlaceOfBirthRegion(toIdNameMap(sd.getPlaceOfBirthRegion(),
                r -> r.getRegionCode(), r -> r.getRegion()));

        // Current address
        dto.setCurrentAddressWoreda(toIdNameMap(sd.getCurrentAddressWoreda(),
                w -> w.getWoredaCode(), w -> w.getWoreda()));
        dto.setCurrentAddressZone(toIdNameMap(sd.getCurrentAddressZone(),
                z -> z.getZoneCode(), z -> z.getZone()));
        dto.setCurrentAddressRegion(toIdNameMap(sd.getCurrentAddressRegion(),
                r -> r.getRegionCode(), r -> r.getRegion()));

        // Additional personal info
        dto.setEmail(sd.getEmail());
        dto.setMaritalStatus(sd.getMaritalStatus());

        // Foreign entities with possible null values
        dto.setImpairment(toIdNameMap(sd.getImpairment(),
                i -> i.getImpairmentCode(), i -> i.getImpairment()));

        dto.setSchoolBackground(toIdNameMap(sd.getSchoolBackground(),
                SchoolBackground::getId, SchoolBackground::getBackground));

        // Emergency contact
        dto.setContactPersonFirstNameAMH(sd.getContactPersonFirstNameAMH());
        dto.setContactPersonFirstNameENG(sd.getContactPersonFirstNameENG());
        dto.setContactPersonLastNameAMH(sd.getContactPersonLastNameAMH());
        dto.setContactPersonLastNameENG(sd.getContactPersonLastNameENG());
        dto.setContactPersonPhoneNumber(sd.getContactPersonPhoneNumber());
        dto.setContactPersonRelation(sd.getContactPersonRelation());

        // Academic information
        dto.setDateEnrolledEC(sd.getDateEnrolledEC());
        dto.setDateEnrolledGC(sd.getDateEnrolledGC());

        dto.setAcademicYear(toIdNameMap(sd.getAcademicYear(),
                AcademicYear::getYearCode, ay -> ay.getAcademicYearGC()));

        dto.setBatch(toIdNameMap(sd.getBatch(),
            Batch::getId, Batch::getBatchName));

        dto.setBatchClassYearSemester(toIdNameMap(sd.getBatchClassYearSemester(),
                BatchClassYearSemester::getBcysID,
                BatchClassYearSemester::getDisplayName));

        // Add recent batch info (from BatchClassYearSemester → Batch)
        if (sd.getBatchClassYearSemester() != null && sd.getBatchClassYearSemester().getBatch() != null) {
            Batch batch = sd.getBatchClassYearSemester().getBatch();
            Map<String, Object> batchMap = new HashMap<>();
            batchMap.put("id", batch.getId());
            batchMap.put("name", batch.getBatchName());
            dto.setRecentBatch(batchMap);
        } else {
            dto.setRecentBatch(null);
        }

        dto.setStudentRecentStatus(toIdNameMap(sd.getStudentRecentStatus(),
                s -> s.getId(), s -> s.getStatusName())); // assuming StudentStatus has getId() and getStatusName()

        dto.setDepartmentEnrolled(toIdNameMap(sd.getDepartmentEnrolled(),
                Department::getDptID, Department::getDeptName));

        dto.setProgramModality(toIdNameMap(sd.getProgramModality(),
                ProgramModality::getModalityCode, ProgramModality::getModality));

        dto.setProgramLevel(toIdNameMap(
            sd.getProgramModality() != null ? sd.getProgramModality().getProgramLevel() : null,
            ProgramLevel::getCode, ProgramLevel::getName));

        // Document & academic status
        dto.setDocumentStatus(sd.getDocumentStatus());
        dto.setGrade12Result(sd.getGrade12Result());
        dto.setRemark(sd.getRemark());
        dto.setTransfer(sd.isTransfer());
        dto.setExitExamUserID(sd.getExitExamUserID());
        dto.setExitExamScore(sd.getExitExamScore());
        dto.setIsStudentPassExitExam(sd.getIsStudentPassExitExam());
        dto.setYearOfExamG12(sd.getYearOfExamG12());
        dto.setNationalexamIdG12(sd.getNationalexamIdG12());
        dto.setDateClassEndGC(sd.getDateClassEndGC());
        dto.setDateGraduated(sd.getDateGraduated());
        dto.setEntryYearGC(sd.getEntryYearGC());
        dto.setEntryYearEC(sd.getEntryYearEC());

        dto.setCgpa(sd.getCgpa());                          // can be null
        dto.setTotalEarnedCreditHours(sd.getTotalEarnedCreditHours());  // can be null or 0
        dto.setTotalCoursesRegistered(sd.getTotalNumberOfCoursesTaken());

        return dto;
    }

    /**
     * Generic helper to safely create {id, name} map for any entity.
     * Prevents NullPointerException if the referenced entity is null.
     */
    private <T> Map<String, Object> toIdNameMap(T entity,
                                               Function<T, Object> idExtractor,
                                               Function<T, Object> nameExtractor) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", idExtractor.apply(entity));
        map.put("name", nameExtractor.apply(entity));
        return map;
    }

    private String buildFullName(String firstPart, String secondPart) {
        String first = firstPart != null ? firstPart.trim() : "";
        String second = secondPart != null ? secondPart.trim() : "";
        return (first + " " + second).trim();
    }

    //==================================================================================================================
    /**
     * Calculates and persists CGPA + total registered credit hours + total courses taken
     * for every student in the system.
     *
     * Main goals:
     * - One-time bulk refresh or initialization (usually after grade release periods)
     * - Uses department-specific or fallback GradingSystem
     * - Safe: skips invalid students, handles missing data gracefully
     * - Atomic per student (inner try-catch + save)
     *
     * Performance notes:
     * - Fetches all students once (good)
     * - For very large institutions, consider batching or async processing later
     * - Grading system lookup is per student — could be pre-cached per department if bottleneck
     *
     * @return summary map with stats (totalStudents, success, failed, message, errors if any)
     */
    @Transactional
    public Map<String, Object> calculateAndSaveCgpaForAllStudents() {

        List<StudentDetails> allStudents = studentDetailsRepository.findAll();

        if (allStudents.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("message", "No students found in the system");
            result.put("totalStudents", 0);
            result.put("success", 0);
            result.put("failed", 0);
            result.put("errors", null);
            return result;
        }

        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();

        for (StudentDetails studentDetails : allStudents) {

            User studentUser = studentDetails.getUser();
            if (studentUser == null) {
                failedCount++;
                errors.add("StudentDetails id=" + studentDetails.getId() + " has no linked User");
                continue;
            }

            try {
                // Get the applicable grading system once per student
                GradingSystem gradingSystem = gradingSystemService.findApplicableGradingSystem(
                        studentDetails.getDepartmentEnrolled()
                );

                // Calculate CGPA using full history (null = all released semesters)
                double cgpa = studentCopyService.calculateCGPA(
                        studentUser,
                        null,               // full history
                        gradingSystem
                );

                // Update CGPA with 2 decimal places
                studentDetails.setCgpa(
                        BigDecimal.valueOf(cgpa)
                                .setScale(2, RoundingMode.HALF_UP)
                );

                // Total registered (attempted) credit hours - no pass check
                int totalRegisteredCr = calculateTotalRegisteredCreditHours(studentUser);
                studentDetails.setTotalEarnedCreditHours(Math.max(totalRegisteredCr, 0));

                // Total number of courses the student has ever been registered for
                long courseCount = studentCourseScoreRepo.countByStudent(studentUser);
                studentDetails.setTotalNumberOfCoursesTaken((int) courseCount); // 0 if none

                // Persist changes
                studentDetailsRepository.save(studentDetails);

                successCount++;

            } catch (IllegalStateException e) {
                failedCount++;
                errors.add("Student " + studentUser.getUsername() + ": " + e.getMessage());
            } catch (Exception e) {
                failedCount++;
                String msg = "Student " + studentUser.getUsername() + ": unexpected error - " + e.getMessage();
                errors.add(msg);
                // Optional: log full stack trace in production
                // log.error(msg, e);
            }
        }

        // Build consistent response (same shape even on partial failure)
        Map<String, Object> result = new HashMap<>();
        result.put("message", "CGPA + course count refresh completed");
        result.put("totalStudents", allStudents.size());
        result.put("success", successCount);
        result.put("failed", failedCount);
        result.put("errors", errors.isEmpty() ? null : errors);

        return result;
    }

    /**
     * Calculates total credit hours from all courses the student has ever been registered for.
     * Sums theoryHrs + labHrs for every row in student_course_score for this student.
     * No filtering by score presence, release status, or pass/fail result.
     */
    private int calculateTotalRegisteredCreditHours(User student) {
        List<StudentCourseScore> registrations = studentCourseScoreRepo.findByStudent(student);

        int total = 0;

        for (StudentCourseScore reg : registrations) {
            Course c = reg.getCourse();
            if (c != null) {
                total += c.getTheoryHrs() + c.getLabHrs();
            }
        }

        return total;
    }

    /**
     * Retrieves academic progress summary for a student including taken and remaining courses.
     * Optimized to minimize database roundtrips and avoid N+1 issues.
     *
     * @param studentUserId the ID of the User entity linked to StudentDetails
     * @return StudentAcademicProgressDTO with all calculated data
     * @throws ResourceNotFoundException if student not found
     * @throws BadRequestException if invalid user ID
     */
    public StudentAcademicProgressDTO getStudentAcademicProgress(Long studentUserId) {

        if (studentUserId == null || studentUserId <= 0) {
            throw new BadRequestException("Invalid student user ID");
        }

        // Fetch StudentDetails with necessary eager fetching or JOIN FETCH
        StudentDetails studentDetails = studentDetailsRepository.findWithUserAndDepartmentAndStatusAndBcys(studentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with user ID: " + studentUserId));

        User user = studentDetails.getUser();
        Department department = studentDetails.getDepartmentEnrolled();
        BatchClassYearSemester currentBcys = studentDetails.getBatchClassYearSemester();
        StudentStatus status = studentDetails.getStudentRecentStatus();

        // 1. Get all taken courses (StudentCourseScore entries) for this student
        // We fetch all in one query with JOIN FETCH to avoid N+1
        List<StudentCourseScore> takenScores = studentCourseScoreRepo.findByStudentWithCourseAndSourceAndBcys(user.getId());

        // Build taken courses list + calculate totals
        List<TakenCourseDTO> takenDtos = new ArrayList<>();
        int totalTakenCr = 0;

        for (StudentCourseScore score : takenScores) {
            Course course = score.getCourse();
            BatchClassYearSemester bcys = score.getBatchClassYearSemester();
            CourseSource source = score.getCourseSource();

            int crHrs = (course.getTheoryHrs() != null ? course.getTheoryHrs() : 0) +
                    (course.getLabHrs() != null ? course.getLabHrs() : 0);

            takenDtos.add(TakenCourseDTO.builder()
                    .courseId(course.getCID())
                    .courseCode(course.getCCode())
                    .courseTitle(course.getCTitle())
                    .creditHours(crHrs)
                    .courseSource(source.getSourceName())
                    .isReleased(score.isReleased())
                    .takenIn(bcys.getDisplayName())
                    .build());

            totalTakenCr += crHrs;
        }

        // 2. Get ALL courses that belong to the student's department
        // We assume these are the curriculum courses the student must take
        List<Course> allDepartmentCourses = courseRepository.findByDepartment(department);

        // 3. Build set of taken course IDs for fast lookup
        Set<Long> takenCourseIds = takenScores.stream()
                .map(s -> s.getCourse().getCID())
                .collect(Collectors.toSet());

        // 4. Calculate remaining courses
        List<RemainingCourseDTO> remainingDtos = new ArrayList<>();
        int totalRemainingCr = 0;

        for (Course course : allDepartmentCourses) {
            if (!takenCourseIds.contains(course.getCID())) {

                int crHrs = (course.getTheoryHrs() != null ? course.getTheoryHrs() : 0) +
                        (course.getLabHrs() != null ? course.getLabHrs() : 0);

                String expectedIn = null;
                if (course.getClassYear() != null && course.getSemester() != null) {
                    expectedIn = course.getClassYear().getClassYear() + " - " +
                            course.getSemester().getAcademicPeriodCode();
                }

                remainingDtos.add(RemainingCourseDTO.builder()
                        .courseId(course.getCID())
                        .courseCode(course.getCCode())
                        .courseTitle(course.getCTitle())
                        .creditHours(crHrs)
                        .expectedIn(expectedIn)
                        .build());

                totalRemainingCr += crHrs;
            }
        }

        // Build full response
        String fullName = String.format("%s %s %s",
                studentDetails.getFirstNameENG(),
                studentDetails.getFatherNameENG(),
                studentDetails.getGrandfatherNameENG());

        return StudentAcademicProgressDTO.builder()
                .studentId(user.getId())
                .username(user.getUsername())
                .fullName(fullName.trim())
                .department(department.getDeptName())   // assuming you have this field
                .currentStatus(status.getStatusName())
                .currentBatchClassYearSemester(currentBcys.getDisplayName())
                .takenCourses(takenDtos)
                .totalTakenCourses(takenDtos.size())
                .totalTakenCreditHours(totalTakenCr)
                .remainingCourses(remainingDtos)
                .totalRemainingCourses(remainingDtos.size())
                .totalRemainingCreditHours(totalRemainingCr)
                .build();
    }

    @EventListener
    @Async   // ← very important: run in background, don't block HTTP response
    @Transactional
    public void handleScoreUpdate(ScoreUpdatedEvent event) {
        Long studentId = event.getStudentUserId();

        try {
            User student = userRepository.findById(studentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));

            StudentDetails details = studentDetailsRepository.findByUser(student)
                    .orElseThrow(() -> new ResourceNotFoundException("Student details not found"));

            Department dept = details.getDepartmentEnrolled();
            GradingSystem gs = gradingSystemService.findApplicableGradingSystem(dept);

            double newCgpa = studentCopyService.calculateCGPA(student, details.getBatchClassYearSemester(), gs);
            int newTotalCr = calculateTotalRegisteredCreditHours(student);

            details.setCgpa(BigDecimal.valueOf(newCgpa).setScale(2, RoundingMode.HALF_UP));
            details.setTotalEarnedCreditHours(Math.max(newTotalCr, 0));

            // ── NEW ── Total number of courses taken
            long courseCount = studentCourseScoreRepo.countByStudent(student);
            details.setTotalNumberOfCoursesTaken(
                    courseCount > 0 ? (int) courseCount : 0
            );

            studentDetailsRepository.save(details);

            // Optional: log success
            /*log.info("CGPA updated for student {} → CGPA: {}, Credits: {}",
                    student.getUsername(), newCgpa, newTotalCr);*/

        } catch (Exception e) {
            System.out.println("Failed to update CGPA for student " + studentId + ": " + e.getMessage());
//            log.error("Failed to update CGPA for student {}: {}", studentId, e.getMessage(), e);
            // You can add retry logic or send admin notification here if critical
        }
    }
}