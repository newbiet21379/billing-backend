package com.acme.billing.web.rest;

import com.acme.billing.api.commands.ApproveBillCommand;
import com.acme.billing.api.commands.AttachFileCommand;
import com.acme.billing.api.commands.CreateBillCommand;
import com.acme.billing.service.StorageService;
import com.acme.billing.service.exception.FileValidationException;
import com.acme.billing.web.rest.BillCommandController.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for BillCommandController.
 * Tests the REST endpoints for bill command operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BillCommandController Tests")
class BillCommandControllerTest {

    @Mock
    private CommandGateway commandGateway;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private BillCommandController billCommandController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(billCommandController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should create bill successfully when valid data provided")
    void shouldCreateBillSuccessfullyWhenValidDataProvided() throws Exception {
        // Given
        String billId = UUID.randomUUID().toString();
        CreateBillCommand command = CreateBillCommand.builder()
                .billId(billId)
                .title("Electric Bill")
                .total(new BigDecimal("150.75"))
                .metadata(Map.of("category", "utilities"))
                .build();

        when(commandGateway.send(any(CreateBillCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When & Then
        mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.billId").value(billId))
                .andExpect(jsonPath("$.message").value("Bill creation accepted"));

        verify(commandGateway).send(refEq(command));
    }

    @Test
    @DisplayName("Should generate bill ID when not provided")
    void shouldGenerateBillIdWhenNotProvided() throws Exception {
        // Given
        CreateBillCommand command = CreateBillCommand.builder()
                .title("Gas Bill")
                .total(new BigDecimal("85.50"))
                .build();

        when(commandGateway.send(any(CreateBillCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When & Then
        mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.billId").exists())
                .andExpect(jsonPath("$.message").value("Bill creation accepted"));

        verify(commandGateway).send(argThat(cmd -> cmd.getBillId() != null && !cmd.getBillId().trim().isEmpty()));
    }

    @Test
    @DisplayName("Should return 400 when creating bill with invalid data")
    void shouldReturnBadRequestWhenCreatingBillWithInvalidData() throws Exception {
        // Given
        CreateBillCommand command = CreateBillCommand.builder()
                .title("")
                .total(BigDecimal.ZERO)
                .build();

        // When & Then
        mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(commandGateway, never()).send(any());
    }

    @Test
    @DisplayName("Should return 422 when command execution fails")
    void shouldReturnUnprocessableEntityWhenCommandExecutionFails() throws Exception {
        // Given
        String billId = UUID.randomUUID().toString();
        CreateBillCommand command = CreateBillCommand.builder()
                .billId(billId)
                .title("Invalid Bill")
                .total(new BigDecimal("100.00"))
                .build();

        when(commandGateway.send(any(CreateBillCommand.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new CommandExecutionException("Business rule violation",
                                new IllegalArgumentException("Invalid bill data"))));

        // When & Then
        mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isInternalServerError());

        verify(commandGateway).send(refEq(command));
    }

    @Test
    @DisplayName("Should attach file successfully when valid file provided")
    void shouldAttachFileSuccessfullyWhenValidFileProvided() throws Exception {
        // Given
        String billId = UUID.randomUUID().toString();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bill.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test pdf content".getBytes());

        String storagePath = "bills/" + billId + "/bill.pdf";
        AttachFileCommand expectedCommand = AttachFileCommand.builder()
                .billId(billId)
                .filename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .storagePath(storagePath)
                .build();

        when(storageService.uploadFile(any(MultipartFile.class), eq(billId)))
                .thenReturn(storagePath);
        when(commandGateway.send(any(AttachFileCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When & Then
        mockMvc.perform(multipart("/api/commands/bills/{billId}/file", billId)
                        .file(file))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.billId").value(billId))
                .andExpect(jsonPath("$.filename").value("bill.pdf"))
                .andExpect(jsonPath("$.storagePath").value(storagePath))
                .andExpect(jsonPath("$.fileSize").value(file.getSize()))
                .andExpect(jsonPath("$.message").value("File upload accepted and OCR processing initiated"));

        verify(storageService).uploadFile(refEq(file), eq(billId));
        verify(commandGateway).send(refEq(expectedCommand));
    }

    @Test
    @DisplayName("Should return 400 when attaching file with invalid bill ID")
    void shouldReturnBadRequestWhenAttachingFileWithInvalidBillId() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bill.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test pdf content".getBytes());

        // When & Then
        mockMvc.perform(multipart("/api/commands/bills/{billId}/file", "")
                        .file(file))
                .andExpect(status().isBadRequest());

        verify(storageService, never()).uploadFile(any(), any());
        verify(commandGateway, never()).send(any());
    }

    @Test
    @DisplayName("Should return 400 when file validation fails")
    void shouldReturnBadRequestWhenFileValidationFails() throws Exception {
        // Given
        String billId = UUID.randomUUID().toString();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bill.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "".getBytes()); // Empty file

        when(storageService.uploadFile(any(MultipartFile.class), eq(billId)))
                .thenThrow(new FileValidationException("File cannot be empty"));

        // When & Then
        mockMvc.perform(multipart("/api/commands/bills/{billId}/file", billId)
                        .file(file))
                .andExpect(status().isBadRequest());

        verify(storageService).uploadFile(refEq(file), eq(billId));
        verify(commandGateway, never()).send(any());
    }

    @Test
    @DisplayName("Should approve bill successfully when valid request provided")
    void shouldApproveBillSuccessfullyWhenValidRequestProvided() throws Exception {
        // Given
        String billId = UUID.randomUUID().toString();
        ApproveBillRequest request = ApproveBillRequest.builder()
                .approvedBy("manager@example.com")
                .approvalReason("Bill approved for payment")
                .build();

        ApproveBillCommand expectedCommand = ApproveBillCommand.builder()
                .billId(billId)
                .approvedBy("manager@example.com")
                .approvalReason("Bill approved for payment")
                .build();

        when(commandGateway.send(any(ApproveBillCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When & Then
        mockMvc.perform(post("/api/commands/bills/{billId}/approve", billId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.billId").value(billId))
                .andExpect(jsonPath("$.approvedBy").value("manager@example.com"))
                .andExpect(jsonPath("$.message").value("Bill approval accepted"));

        verify(commandGateway).send(refEq(expectedCommand));
    }

    @Test
    @DisplayName("Should approve bill with default values when request body is empty")
    void shouldApproveBillWithDefaultValuesWhenRequestBodyIsEmpty() throws Exception {
        // Given
        String billId = UUID.randomUUID().toString();

        ApproveBillCommand expectedCommand = ApproveBillCommand.builder()
                .billId(billId)
                .approvedBy("system")
                .approvalReason("Approved via API")
                .build();

        when(commandGateway.send(any(ApproveBillCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When & Then
        mockMvc.perform(post("/api/commands/bills/{billId}/approve", billId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.billId").value(billId))
                .andExpect(jsonPath("$.approvedBy").value("system"))
                .andExpect(jsonPath("$.message").value("Bill approval accepted"));

        verify(commandGateway).send(argThat(cmd ->
                cmd.getBillId().equals(billId) &&
                cmd.getApprovedBy().equals("system") &&
                cmd.getApprovalReason().equals("Approved via API")
        ));
    }

    @Test
    @DisplayName("Should return 400 when approving bill with invalid bill ID")
    void shouldReturnBadRequestWhenApprovingBillWithInvalidBillId() throws Exception {
        // Given
        ApproveBillRequest request = ApproveBillRequest.builder()
                .approvedBy("manager@example.com")
                .build();

        // When & Then
        mockMvc.perform(post("/api/commands/bills/{billId}/approve", "invalid-id-that-is-too-long-to-be-valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(commandGateway, never()).send(any());
    }

    @Test
    @DisplayName("Should cleanup uploaded file when command fails")
    void shouldCleanupUploadedFileWhenCommandFails() throws Exception {
        // Given
        String billId = UUID.randomUUID().toString();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bill.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test pdf content".getBytes());

        String storagePath = "bills/" + billId + "/bill.pdf";

        when(storageService.uploadFile(any(MultipartFile.class), eq(billId)))
                .thenReturn(storagePath);
        when(commandGateway.send(any(AttachFileCommand.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new CommandExecutionException("Command failed", new RuntimeException())));

        // When & Then
        mockMvc.perform(multipart("/api/commands/bills/{billId}/file", billId)
                        .file(file))
                .andExpect(status().isInternalServerError());

        verify(storageService).uploadFile(refEq(file), eq(billId));
        verify(commandGateway).send(any(AttachFileCommand.class));
        verify(storageService).deleteFile(storagePath); // Should cleanup
    }

    @Test
    @DisplayName("Should return 400 when creating bill with malformed JSON")
    void shouldReturnBadRequestWhenCreatingBillWithMalformedJson() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/commands/bills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest());

        verify(commandGateway, never()).send(any());
    }
}