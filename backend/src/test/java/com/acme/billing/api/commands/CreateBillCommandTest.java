package com.acme.billing.api.commands;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CreateBillCommandTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Test
    void shouldPassValidationWhenAllFieldsAreValid() {
        CreateBillCommand command = CreateBillCommand.builder()
                .billId("test-bill-id")
                .title("Test Bill")
                .total(new BigDecimal("100.00"))
                .build();

        Set<ConstraintViolation<CreateBillCommand>> violations = validator.validate(command);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenBillIdIsBlank() {
        CreateBillCommand command = CreateBillCommand.builder()
                .billId("")
                .title("Test Bill")
                .total(new BigDecimal("100.00"))
                .build();

        Set<ConstraintViolation<CreateBillCommand>> violations = validator.validate(command);
        assertEquals(1, violations.size());
        assertEquals("Bill ID is required", violations.iterator().next().getMessage());
    }

    @Test
    void shouldFailValidationWhenTitleIsBlank() {
        CreateBillCommand command = CreateBillCommand.builder()
                .billId("test-bill-id")
                .title("")
                .total(new BigDecimal("100.00"))
                .build();

        Set<ConstraintViolation<CreateBillCommand>> violations = validator.validate(command);
        assertEquals(1, violations.size());
        assertEquals("Title is required", violations.iterator().next().getMessage());
    }

    @Test
    void shouldFailValidationWhenTotalIsZero() {
        CreateBillCommand command = CreateBillCommand.builder()
                .billId("test-bill-id")
                .title("Test Bill")
                .total(BigDecimal.ZERO)
                .build();

        Set<ConstraintViolation<CreateBillCommand>> violations = validator.validate(command);
        assertEquals(1, violations.size());
        assertEquals("Total amount must be greater than 0", violations.iterator().next().getMessage());
    }

    @Test
    void shouldCreateCommandUsingStaticFactoryMethod() {
        CreateBillCommand command = CreateBillCommand.create(
                "test-bill-id",
                "Test Bill",
                new BigDecimal("100.00"),
                null
        );

        assertEquals("test-bill-id", command.getBillId());
        assertEquals("Test Bill", command.getTitle());
        assertEquals(new BigDecimal("100.00"), command.getTotal());
        assertNotNull(command.getMetadata());
        assertTrue(command.getMetadata().isEmpty());
    }
}