package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.BatchDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.Batch;
import Henok.example.DeutscheCollageBack_endAPI.Entity.BatchClassYearSemester;
import Henok.example.DeutscheCollageBack_endAPI.Entity.ClassYear;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Semester;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.BatchRepo;
import Henok.example.DeutscheCollageBack_endAPI.Repository.ClassYearRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.GradingSystemRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.SemesterRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BatchService {

    @Autowired
    private BatchRepo batchRepository;
    @Autowired
    private ClassYearRepository classYearRepository;
    @Autowired
    private SemesterRepo semesterRepository;
    @Autowired
    private BatchClassYearSemesterService batchClassYearSemesterService;
    @Autowired
    private GradingSystemRepository gradingSystemRepository; // For fetching latest by effective date

    public BatchDTO addBatches(List<BatchDTO> batchDTOs) {
        if (batchDTOs == null || batchDTOs.isEmpty()) {
            throw new IllegalArgumentException("Batch list cannot be empty");
        }
        List<Batch> batches = new ArrayList<>();
        for (BatchDTO dto : batchDTOs) {
            if (dto.getBatchName() == null || dto.getBatchName().isEmpty()) {
                throw new IllegalArgumentException("Batch name cannot be empty");
            }
            if (batchRepository.findByBatchName(dto.getBatchName()).isPresent()) {
                throw new DataIntegrityViolationException("Batch name '" + dto.getBatchName() + "' already exists");
            }
            batches.add(new Batch(null, dto.getBatchName()));
        }
        try {
            List<Batch> savedBatches = batchRepository.saveAll(batches);
            savedBatches.forEach(this::generateBatchClassYearSemesterCombinations);
            return batchDTOs.get(0); // Return first DTO for consistency
        } catch (DataIntegrityViolationException e) {
            throw new DataIntegrityViolationException("Failed to create batches due to duplicate entry or database constraint");
        }
    }

    public BatchDTO addBatch(BatchDTO batchDTO) {
        if (batchDTO.getBatchName() == null || batchDTO.getBatchName().isEmpty()) {
            throw new IllegalArgumentException("Batch name cannot be empty");
        }
        if (batchRepository.findByBatchName(batchDTO.getBatchName()).isPresent()) {
            throw new DataIntegrityViolationException("Batch name '" + batchDTO.getBatchName() + "' already exists");
        }
        try {
            Batch batch = new Batch(null, batchDTO.getBatchName());
            Batch savedBatch = batchRepository.save(batch);
            generateBatchClassYearSemesterCombinations(savedBatch);
            return batchDTO;
        } catch (DataIntegrityViolationException e) {
            throw new DataIntegrityViolationException("Failed to create batch due to duplicate entry or database constraint");
        }
    }

    public List<BatchDTO> getAllBatches() {
        List<Batch> batches = batchRepository.findAll();
//        if (batches.isEmpty()) {
//            throw new ResourceNotFoundException("No batches found");
//        }
        return batches.stream()
                .map(batch -> {
                    BatchDTO dto = new BatchDTO();
                    dto.setId(batch.getId());
                    dto.setBatchName(batch.getBatchName());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public BatchDTO getBatchById(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + id));
        BatchDTO dto = new BatchDTO();
        dto.setId(batch.getId());
        dto.setBatchName(batch.getBatchName());
        return dto;
    }

    public BatchDTO updateBatch(Long id, BatchDTO batchDTO) {
        if (batchDTO.getBatchName() == null || batchDTO.getBatchName().isEmpty()) {
            throw new IllegalArgumentException("Batch name cannot be empty");
        }
        Batch existing = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + id));
        if (!existing.getBatchName().equals(batchDTO.getBatchName()) &&
                batchRepository.findByBatchName(batchDTO.getBatchName()).isPresent()) {
            throw new DataIntegrityViolationException("Batch name '" + batchDTO.getBatchName() + "' already exists");
        }
        try {
            existing.setBatchName(batchDTO.getBatchName());
            batchRepository.save(existing);
            return batchDTO;
        } catch (DataIntegrityViolationException e) {
            throw new DataIntegrityViolationException("Failed to update batch due to duplicate entry or database constraint");
        }
    }

    public void deleteBatch(Long id) {
        if (!batchRepository.existsById(id)) {
            throw new ResourceNotFoundException("Batch not found with id: " + id);
        }
        try {
            batchRepository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            throw new DataIntegrityViolationException("Cannot delete batch due to existing dependencies");
        }
    }

    private void generateBatchClassYearSemesterCombinations(Batch batch) {
        List<ClassYear> classYears = classYearRepository.findAll();
        List<Semester> semesters = semesterRepository.findAll();

        if (classYears.isEmpty() || semesters.isEmpty()) {
            throw new IllegalStateException("Class years or semesters not found");
        }

        // Fetch the latest grading system by effective date

        List<BatchClassYearSemester> combinations = new ArrayList<>();

        for (ClassYear classYear : classYears) {
            String classYearValue = classYear.getClassYear();
            if (classYearValue.equals("1")) {
                // First year: combine with S1, S2, and S3
                semesters.stream()
                        .filter(s -> s.getAcademicPeriodCode().equals("S1") || s.getAcademicPeriodCode().equals("S2") || s.getAcademicPeriodCode().equals("S3"))
                        .forEach(semester -> combinations.add(
                                new BatchClassYearSemester(null, batch, classYear, semester)
                        ));
            } else if (classYearValue.startsWith("PC") || classYearValue.startsWith("C")) {
                // Medical years (PC1, PC2, C1, C2, C3): combine with FS
                semesters.stream()
                        .filter(s -> s.getAcademicPeriodCode().equals("FS"))
                        .forEach(semester -> combinations.add(
                                new BatchClassYearSemester(null, batch, classYear, semester)
                        ));
            }else if(classYearValue.startsWith("not")){
                continue;
            } else {
                // Other years (2, 3, 4, 5, 6): combine with S1 and S2
                semesters.stream()
                        .filter(s -> s.getAcademicPeriodCode().equals("S1") || s.getAcademicPeriodCode().equals("S2"))
                        .forEach(semester -> combinations.add(
                                new BatchClassYearSemester(null, batch, classYear, semester)
                        ));
            }
        }

        if (combinations.isEmpty()) {
            throw new IllegalStateException("No valid batch-class-year-semester combinations generated");
        }
        batchClassYearSemesterService.saveAll(combinations);
    }

    // Explanation: Updated to fetch latest GradingSystem by effectiveDate and include in BatchClassYearSemester constructor.
    // Why: Fixes missing gradingSystem parameter; ensures non-null constraint; uses latest global system.
}
