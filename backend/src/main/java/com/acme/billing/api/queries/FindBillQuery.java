package com.acme.billing.api.queries;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FindBillQuery {

    @NotBlank(message = "Bill ID is required")
    @Size(max = 36, message = "Bill ID must be at most 36 characters")
    private String billId;

    @JsonCreator
    public FindBillQuery(@JsonProperty("billId") String billId) {
        this.billId = billId;
    }
}