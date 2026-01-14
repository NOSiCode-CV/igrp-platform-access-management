package cv.igrp.platform.access_management.files.application.commands.handler;

import cv.igrp.platform.access_management.files.application.commands.UploadPrivateFileCommand;
import cv.igrp.platform.access_management.files.application.commands.UploadPublicFileCommand;
import cv.igrp.platform.access_management.files.application.constants.UploadType;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.filemanager.StorageService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UploadFileCommandHandlerTest {

    @InjectMocks
    private UploadFileCommandHandler uploadFileCommandHandler;

    @Mock
    private StorageService storageService;

    @Mock
    private AuthenticationHelper authenticationHelper;

    @Mock MultipartFile file;

    private static final String USER = "testUser";
    private static final UUID FIXED_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() throws Exception {

    }

    @Test
    void testUploadPrivateFile_shouldThrowException_whenFileIsNull() {
        UploadPrivateFileCommand command = new UploadPrivateFileCommand(null, "private_folder");
        Set<ConstraintViolation<UploadPrivateFileCommand>> violations = validate(command);

        assertFalse(violations.isEmpty(), "Deveria falhar quando file é null");
        assertEquals("The field <file> is required", violations.iterator().next().getMessage());
    }

    @Test
    void testUploadPublicFile_shouldThrowException_whenFileIsNull() {
        UploadPublicFileCommand command = new UploadPublicFileCommand(null, "public_folder");
        Set<ConstraintViolation<UploadPublicFileCommand>> violations = validate(command);

        assertFalse(violations.isEmpty(), "Deveria falhar quando file é null");
        assertEquals("The field <file> is required", violations.iterator().next().getMessage());
    }

    @Test
    void testUploadFile_shouldThrowException_whenFileIsEmpty_Public() {
        assertUploadFailsWhenFileIsEmpty(UploadType.PUBLIC, "public_folder");
    }

    @Test
    void testUploadFile_shouldThrowException_whenFileIsEmpty_Private() {
        assertUploadFailsWhenFileIsEmpty(UploadType.PRIVATE, "private_folder");
    }

    @Test
    void testUploadFile_shouldThrowRuntimeException_whenIOExceptionOnGetBytes() throws IOException {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getBytes()).thenThrow(new IOException("Falha na leitura do arquivo"));
        when(authenticationHelper.getSub()).thenReturn(USER);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                //Tanto publica quanto privada
                () -> uploadFileCommandHandler.uploadFile(file, "pasta", UploadType.PUBLIC)
        );

        assertEquals("Error reading file content", exception.getMessage());

        assertInstanceOf(IOException.class, exception.getCause());
        assertEquals("Falha na leitura do arquivo", exception.getCause().getMessage());
    }

    @Test
    void testUploadFile_shouldUploadPrivateFileSuccessfully() throws Exception {
        String folder = "documents";
        String originalFilename = "contract.pdf";
        String contentType = "application/pdf";
        String content = "Test content";

        mockFile(originalFilename, contentType, content);
        when(authenticationHelper.getSub()).thenReturn(USER);

        try (MockedStatic<UUID> mockedUuid = mockStatic(UUID.class)) {
            mockedUuid.when(UUID::randomUUID).thenReturn(FIXED_UUID);

            String resultPath = uploadFileCommandHandler.uploadFile(file, folder, UploadType.PRIVATE);

            assertPathEquals("private", "documents", "contract.pdf", resultPath);

            verify(storageService).uploadFile(any(), eq(resultPath), any());

        }
    }

    private void mockFile(String filename, String type, String content) throws Exception {
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getContentType()).thenReturn(type);
        when(file.getBytes()).thenReturn(content.getBytes());
    }

    private void assertPathEquals(String type, String folder, String filename, String actualPath) {
        String expected = String.format("%s/%s/%s/%s_%s",
                type, folder, USER, FIXED_UUID, filename);
        assertEquals(expected, actualPath);
    }

    @Test
    void testUploadFile_shouldUploadPublicFileSuccessfully() throws Exception {
        // Arrange
        String folder = "public_docs";
        String originalFilename = "document.pdf";
        String contentType = "application/pdf";
        String content = "Public file content";
        mockFile(originalFilename, contentType, content);

        when(authenticationHelper.getSub()).thenReturn(USER);


        try (MockedStatic<UUID> mockedUuid = mockStatic(UUID.class)) {
            mockedUuid.when(UUID::randomUUID).thenReturn(FIXED_UUID);

            // Act
            String resultPath = uploadFileCommandHandler.uploadFile(file, folder, UploadType.PUBLIC);

            assertPathEquals("public", "public_docs", "document.pdf", resultPath);

            verify(storageService).uploadPublicFile(any(), eq(resultPath), any());
        }
    }

    private <T> Set<ConstraintViolation<T>> validate(T command) {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        return validator.validate(command);
    }

    private void assertUploadFailsWhenFileIsEmpty(UploadType uploadType, String folder) {
        when(file.isEmpty()).thenReturn(true);

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () ->
                uploadFileCommandHandler.uploadFile(file, folder, uploadType)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
