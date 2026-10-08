package com.pfm.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pfm.dto.request.TransactionCreateRequest;
import com.pfm.dto.request.TransactionUpdateRequest;
import com.pfm.dto.response.TransactionResponse;
import com.pfm.entity.CategoryType;
import com.pfm.exception.BadRequestException;
import com.pfm.exception.ResourceNotFoundException;
import com.pfm.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice tests for {@link TransactionController}. The service layer is mocked so
 * only HTTP wiring (status codes, JSON shape, request validation) is exercised.
 */
@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    private static TransactionResponse sampleResponse() {
        return new TransactionResponse(1L, new BigDecimal("5000.00"), LocalDate.of(2024, 1, 15), "Salary",
                "January Salary", CategoryType.INCOME);
    }

    @Test
    void createTransactionReturns201WithCreatedBody() throws Exception {
        when(transactionService.createTransaction(any(TransactionCreateRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":5000.00,"date":"2024-01-15","category":"Salary",
                                 "description":"January Salary"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.amount", is(5000.00)))
                .andExpect(jsonPath("$.date", is("2024-01-15")))
                .andExpect(jsonPath("$.category", is("Salary")))
                .andExpect(jsonPath("$.type", is("INCOME")));
    }

    @Test
    void createTransactionWithNegativeAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-100.00,\"date\":\"2024-01-17\",\"category\":\"Food\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransactionWithZeroAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"date\":\"2024-01-17\",\"category\":\"Food\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransactionWithMissingCategoryReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"date\":\"2024-01-17\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransactionWithUnparseableDateReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"date\":\"01-17-2024\",\"category\":\"Food\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransactionWithUnknownCategoryReturns400() throws Exception {
        when(transactionService.createTransaction(any(TransactionCreateRequest.class)))
                .thenThrow(new BadRequestException("Category 'NonExistentCategory' not found"));

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"date\":\"2024-01-18\",\"category\":\"NonExistentCategory\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listTransactionsReturns200WithWrapper() throws Exception {
        when(transactionService.listTransactions(any())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.transactions[0].id", is(1)));
    }

    @Test
    void listTransactionsWithCategoryNameFilterPassesItThrough() throws Exception {
        when(transactionService.listTransactions(any())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/transactions").param("category", "Salary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].category", is("Salary")));
    }

    @Test
    void listTransactionsWithBadStartDateReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions").param("startDate", "bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listTransactionsWithUnknownCategoryIdReturns404() throws Exception {
        when(transactionService.listTransactions(any()))
                .thenThrow(new ResourceNotFoundException("Category 999 not found"));

        mockMvc.perform(get("/api/transactions").param("categoryId", "999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTransactionReturns200WithFullTransaction() throws Exception {
        TransactionResponse updated = new TransactionResponse(1L, new BigDecimal("5500.00"),
                LocalDate.of(2024, 1, 15), "Salary", "Updated January Salary with bonus", CategoryType.INCOME);
        when(transactionService.updateTransaction(Mockito.eq(1L), any(TransactionUpdateRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/transactions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5500.00,\"description\":\"Updated January Salary with bonus\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(5500.00)))
                .andExpect(jsonPath("$.date", is("2024-01-15")));
    }

    @Test
    void updateTransactionWithDateInBodyIgnoresDateAndReturns200() throws Exception {
        // date is not a component of TransactionUpdateRequest and Jackson ignores unknown
        // properties, so a body containing only "date" must still reach the service as a 200.
        TransactionResponse unchanged = sampleResponse();
        when(transactionService.updateTransaction(Mockito.eq(1L), any(TransactionUpdateRequest.class)))
                .thenReturn(unchanged);

        mockMvc.perform(put("/api/transactions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-01-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date", is("2024-01-15")));
    }

    @Test
    void updateTransactionWithNonNumericIdReturns400() throws Exception {
        mockMvc.perform(put("/api/transactions/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMissingTransactionReturns404() throws Exception {
        when(transactionService.updateTransaction(Mockito.eq(999999L), any(TransactionUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Transaction 999999 not found"));

        mockMvc.perform(put("/api/transactions/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000.00}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTransactionReturns200WithMessage() throws Exception {
        mockMvc.perform(delete("/api/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Transaction deleted successfully")));

        verify(transactionService).deleteTransaction(1L);
    }

    @Test
    void deleteTransactionWithNonNumericIdReturns400() throws Exception {
        mockMvc.perform(delete("/api/transactions/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteMissingTransactionReturns404() throws Exception {
        Mockito.doThrow(new ResourceNotFoundException("Transaction 999999 not found"))
                .when(transactionService).deleteTransaction(999999L);

        mockMvc.perform(delete("/api/transactions/999999"))
                .andExpect(status().isNotFound());
    }
}
