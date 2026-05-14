package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.Reports.AcademicSummaryRequestDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Reports.AcademicSummaryBulkResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Reports.AcademicSummaryResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.StudentCopyDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.StudentCopyRequestDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.StudentCopyOptions;
import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.AcademicYear;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AcademicSummaryService {

    @Autowired
    private StudentCourseScoreRepo studentCourseScoreRepo;

    @Autowired
    private StudentDetailsRepository studentDetailsRepository;

    @Autowired
    private BatchClassYearSemesterRepo batchClassYearSemesterRepo;

    @Autowired
    private DepartmentBCYSRepository departmentBCYSRepository;

    @Autowired
    private DepartmentRepo departmentRepository;

    @Autowired
    private StudentCopyService studentCopyService;

    @Autowired
    private GradingSystemService gradingSystemService;


    public AcademicSummaryBulkResponseDTO generateAcademicSummaries(List<AcademicSummaryRequestDTO> requests) {
        List<AcademicSummaryBulkResponseDTO.Item> results = new ArrayList<>();

        if (requests == null || requests.isEmpty()) {
            return new AcademicSummaryBulkResponseDTO(results);
        }

        for (AcademicSummaryRequestDTO request : requests) {
            try {
                AcademicSummaryResponseDTO summary = generateAcademicSummary(request);
                results.add(new AcademicSummaryBulkResponseDTO.Item(request, summary, true, null));
            } catch (Exception e) {
                System.out.println("Skipping academic summary request departmentId=" +
                        (request != null ? request.getDepartmentId() : null) +
                        ", bcysId=" + (request != null ? request.getBcysId() : null) +
                        " due to: " + e.getMessage());
                results.add(new AcademicSummaryBulkResponseDTO.Item(request, null, false, e.getMessage()));
            }
        }

        return new AcademicSummaryBulkResponseDTO(results);
    }

    /**
     * Generates an academic summary report for all students in a given department and BCYS.
     * 
     * @param request Contains departmentId and bcysId
     * @return AcademicSummaryResponseDTO with header info and all students' academic data
     * @throws ResourceNotFoundException if department, BCYS, or DepartmentBCYS not found
     */
    /**
     * Generates an academic summary report for all students in a given department and BCYS.
     */
    public AcademicSummaryResponseDTO generateAcademicSummary(AcademicSummaryRequestDTO request) {
        // 1. Validate and fetch department, BCYS
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id: " + request.getDepartmentId()
                ));

        BatchClassYearSemester bcys = batchClassYearSemesterRepo.findById(request.getBcysId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BatchClassYearSemester not found with id: " + request.getBcysId()
                ));

        // 2. Get DepartmentBCYS
        DepartmentBCYS deptBCYS = departmentBCYSRepository.findByBcysAndDepartment(bcys, department)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No cohort record found for department " + department.getDeptName() +
                                " and BCYS " + bcys.getDisplayName()
                ));

        // 3. Pre-fetch GradingSystem once (optimization)
        GradingSystem gradingSystem = gradingSystemService.findApplicableGradingSystem(department);

        // 4. Get all distinct students with released scores in this department + BCYS
        List<StudentCourseScore> allScoresInBCYS = studentCourseScoreRepo
                .findByBatchClassYearSemesterAndIsReleasedTrue(bcys);

        Map<User, StudentDetails> studentDetailsMap = new HashMap<>();
        for (StudentCourseScore score : allScoresInBCYS) {
            StudentDetails studentDetails = studentDetailsRepository.findByUser(score.getStudent())
                    .orElse(null);
            if (studentDetails != null &&
                    studentDetails.getDepartmentEnrolled().getDptID().equals(department.getDptID())) {
                studentDetailsMap.put(score.getStudent(), studentDetails);
            }
        }

        // 5. Build header info
        AcademicSummaryResponseDTO.HeaderInfo headerInfo = buildHeaderInfo(deptBCYS, bcys);

        // 6. Generate student summaries using optimized options
        List<AcademicSummaryResponseDTO.StudentSummary> studentSummaries = new ArrayList<>();

        StudentCopyOptions summaryOptions = StudentCopyOptions.forAcademicSummary(gradingSystem);

        for (Map.Entry<User, StudentDetails> entry : studentDetailsMap.entrySet()) {
            User student = entry.getKey();
            StudentDetails studentDetails = entry.getValue();
            try {
                StudentCopyRequestDTO copyRequest = new StudentCopyRequestDTO();
                copyRequest.setStudentId(studentDetails.getId());
                copyRequest.setClassYearId(bcys.getClassYear().getId());
                copyRequest.setSemesterId(bcys.getSemester().getAcademicPeriodCode());

                // Use optimized full StudentCopy with pre-fetched grading system
                StudentCopyDTO studentCopy = studentCopyService.generateStudentCopy(copyRequest, summaryOptions);

                AcademicSummaryResponseDTO.StudentSummary summary = convertToStudentSummary(studentCopy, student.getUsername());
                studentSummaries.add(summary);
            } catch (Exception e) {
                System.out.println("Skipping student " + student.getUsername() + " due to: " + e.getMessage());
                continue;
            }
        }

        // 7. Build and return response
        AcademicSummaryResponseDTO response = new AcademicSummaryResponseDTO();
        response.setHeader(headerInfo);
        response.setStudents(studentSummaries);

        return response;
    }

    /**
     * Helper: Builds header information from DepartmentBCYS and BCYS
     */
    private AcademicSummaryResponseDTO.HeaderInfo buildHeaderInfo(DepartmentBCYS deptBCYS, BatchClassYearSemester bcys) {
        AcademicSummaryResponseDTO.HeaderInfo header = new AcademicSummaryResponseDTO.HeaderInfo();

        // DepartmentBCYS has displayName
        header.setDepartmentBcysDisplay(deptBCYS.getDisplayName());

        // Department info
        if (deptBCYS.getDepartment() != null) {
            header.setDepartmentName(deptBCYS.getDepartment().getDeptName());
            header.setDepartmentCode(deptBCYS.getDepartment().getDepartmentCode());
        }

        // Batch, ClassYear and Semester info
        if (bcys.getBatch() != null) {
            header.setBatchName(bcys.getBatch().getBatchName());
        }   
        if (bcys.getClassYear() != null) {
            header.setClassYearName(bcys.getClassYear().getClassYear());
        }
        if (bcys.getSemester() != null) {
            header.setSemesterName(bcys.getSemester().getAcademicPeriod());
        }

        // Academic Year info from DepartmentBCYS
        if (deptBCYS.getAcademicYear() != null) {
            AcademicYear academicYear = deptBCYS.getAcademicYear();
            AcademicSummaryResponseDTO.AcademicYearInfo yearInfo = new AcademicSummaryResponseDTO.AcademicYearInfo();
            yearInfo.setYearCode(academicYear.getYearCode());
            yearInfo.setYearGC(academicYear.getAcademicYearGC());
            header.setAcademicYear(yearInfo);
        }

        return header;
    }

    /**
     * Helper: Converts StudentCopyDTO to StudentSummary
     */
    private AcademicSummaryResponseDTO.StudentSummary convertToStudentSummary(StudentCopyDTO studentCopy, String studentId) {
        AcademicSummaryResponseDTO.StudentSummary summary = new AcademicSummaryResponseDTO.StudentSummary();

        summary.setStudentId(studentId);

        // Convert course grades
        List<AcademicSummaryResponseDTO.CourseInfo> courses = studentCopy.getCourses().stream()
                .map(courseGrade -> new AcademicSummaryResponseDTO.CourseInfo(
                        courseGrade.getCourseCode(),
                        courseGrade.getCourseTitle(),
                        courseGrade.getScore(),
                        courseGrade.getLetterGrade()
                ))
                .collect(Collectors.toList());
        summary.setCourses(courses);

        // GPA and Status info
        summary.setSemesterGPA(studentCopy.getSemesterGPA());
        summary.setSemesterCGPA(studentCopy.getSemesterCGPA());
        summary.setSemesterGPALetter(studentCopy.getSemesterGPALetter());
        summary.setSemesterCGPALetter(studentCopy.getSemesterCGPALetter());
        summary.setPreviousCGPA(studentCopy.getPreviousCGPA());
        summary.setPreviousCGPALetter(studentCopy.getPreviousCGPALetter());
        summary.setStatus(studentCopy.getStatus());

        return summary;
    }
}
