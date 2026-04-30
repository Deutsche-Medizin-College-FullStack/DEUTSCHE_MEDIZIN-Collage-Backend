package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.FormTemplateListDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.FormTemplate;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Error.BadRequestException;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.FormTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// FormTemplateService
// Service layer for managing FormTemplate entities.
// Handles business logic for creating form templates, including validations and PDF checks.
// Delegates persistence to the repository.
@Service
public class FormTemplateService {

    private final FormTemplateRepository formTemplateRepository;

    @Autowired
    public FormTemplateService(FormTemplateRepository formTemplateRepository) {
        this.formTemplateRepository = formTemplateRepository;
    }

    // Creates a new FormTemplate from the provided data.
    // Validates: file presence, PDF format (MIME, extension, magic bytes), name uniqueness, size limit.
    // Why: Centralizes creation logic and ensures data integrity before saving.
    // Throws BadRequestException for invalid inputs.
    public FormTemplate createFormTemplate(MultipartFile file, String name, String description, Set<Role> forRoles, Boolean isDownloadable) {
        // 1. Validate required fields
        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("Form name is required and cannot be empty");
        }

        if (formTemplateRepository.existsByName(name)) {
            throw new BadRequestException("A form template with this name already exists");
        }

        // 2. Validate file
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("PDF file is required");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new BadRequestException("Only .pdf files are allowed");
        }

        if (!"application/pdf".equals(file.getContentType())) {
            throw new BadRequestException("Invalid file type. Only PDF is allowed");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Failed to read file content");
        }

        if (!isValidPdf(bytes)) {
            throw new BadRequestException("File does not appear to be a valid PDF");
        }

        if (bytes.length > 5 * 1024 * 1024) { // 5MB limit
            throw new BadRequestException("File size exceeds 5MB limit");
        }

        // 3. Build and save the entity
        FormTemplate template = FormTemplate.builder()
                .name(name)
                .description(description)
                .fileContent(bytes)
                .forRoles(forRoles != null ? new HashSet<>(forRoles) : new HashSet<>())
                .isDownloadable(isDownloadable)
                .build();

        return formTemplateRepository.save(template);
    }

    /**
     * Returns a list of all form templates, excluding the large fileContent blob.
     * Supports optional filtering by allowed roles (any match returns the template).
     * Only returns basic metadata + allowed roles.
     */
    public List<FormTemplateListDTO> getAllFormTemplates(Set<Role> filterRoles) {

        List<FormTemplate> templates;

        // If no role filter is provided → return all
        if (filterRoles == null || filterRoles.isEmpty()) {
            templates = formTemplateRepository.findAll();
        } else {
            // Find templates where at least one of the filterRoles is allowed
            templates = formTemplateRepository.findByForRolesIn(filterRoles);
        }

        // Map to DTO to avoid sending byte[] fileContent
        return templates.stream()
                .map(this::toListDTO)
                .toList();
    }

    /**
     * Converts FormTemplate entity to a safe DTO (no binary content).
     */
    private FormTemplateListDTO toListDTO(FormTemplate template) {
        FormTemplateListDTO dto = new FormTemplateListDTO();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setForRoles(template.getForRoles());
        dto.setCreatedAt(template.getCreatedAt());
        dto.setUpdatedAt(template.getUpdatedAt());
        dto.setIsDownloadable(template.getIsDownloadable());
        return dto;
    }

    // Updates an existing form template partially.
    // Only provided fields are updated; null/omitted fields remain unchanged.
    // Enforces name uniqueness if name is provided.
    // Returns the updated entity.
    public FormTemplate updateFormTemplate(Long id, MultipartFile file, String name, String description, Set<Role> forRoles, Boolean isDownloadable) {
        FormTemplate template = formTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Form template not found with id: " + id));

        // Update name only if provided and different
        if (name != null && !name.trim().isEmpty()) {
            if (!name.equals(template.getName()) && formTemplateRepository.existsByName(name)) {
                throw new BadRequestException("A form template with name '" + name + "' already exists");
            }
            template.setName(name);
        }

        // Update description if provided
        if (description != null) {
            template.setDescription(description);
        }

        // Update forRoles if provided (replace the set)
        if (forRoles != null) {
            template.setForRoles(new HashSet<>(forRoles));
        }

        // Update isDownloadable if provided
        if (isDownloadable != null) {
            template.setIsDownloadable(isDownloadable);
        }

        // Update file content if a new file is uploaded
        if (file != null && !file.isEmpty()) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
                throw new BadRequestException("Only .pdf files are allowed");
            }

            if (!"application/pdf".equals(file.getContentType())) {
                throw new BadRequestException("Invalid file type. Only PDF is allowed");
            }

            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw new BadRequestException("Failed to read file content");
            }

            if (!isValidPdf(bytes)) {
                throw new BadRequestException("File does not appear to be a valid PDF");
            }

            if (bytes.length > 5 * 1024 * 1024) {
                throw new BadRequestException("File size exceeds 5MB limit");
            }

            template.setFileContent(bytes);
        }

        // updatedAt is automatically handled by @UpdateTimestamp
        return formTemplateRepository.save(template);
    }


    // Retrieves the form template file content for download.
    // Performs role-based authorization check: the current user must have at least one role
    // that is allowed in the template's forRoles list.
    // Throws custom BadRequestException with clear message if not authorized.
    // Returns the file bytes and original filename (or generated name) for streaming.
    public FormTemplateFileResponse getFormTemplateFile(Long id, Authentication authentication) {

        // Find the template
        FormTemplate template = formTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Form template not found with id: " + id));

        // Get current user roles from JWT / SecurityContext
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Set<String> userRoles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.replace("ROLE_", "")) // remove prefix if your authorities have ROLE_
                .collect(Collectors.toSet());

        // Check if user has at least one allowed role
        boolean hasAccess = template.getForRoles().stream()
                .anyMatch(role -> userRoles.contains(role.name()));

        if (!hasAccess) {
            throw new BadRequestException("You do not have permission to download this form. " +
                    "Allowed roles: " + template.getForRoles());
        }

        // Prepare response object with bytes and filename
        String filename = template.getName() + ".pdf";

        return new FormTemplateFileResponse(template.getFileContent(), filename);
    }

    // Helper record/class to carry file bytes + filename
    // You can use this instead of returning byte[] directly
    public record FormTemplateFileResponse(byte[] content, String filename) {}

    // Deletes a form template by ID.
    // No role-based restriction — deletes regardless of forRoles field.
    public void deleteFormTemplate(Long id) {
        if (!formTemplateRepository.existsById(id)) {
            throw new ResourceNotFoundException("Form template not found with id: " + id);
        }
        formTemplateRepository.deleteById(id);
    }

    // Validates if the byte array is a PDF by checking magic bytes.
    // Why: Adds security layer to prevent non-PDF files disguised as PDF.
    private boolean isValidPdf(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        return bytes[0] == (byte) 0x25 && // %
                bytes[1] == (byte) 0x50 && // P
                bytes[2] == (byte) 0x44 && // D
                bytes[3] == (byte) 0x46;  // F
    }
}
