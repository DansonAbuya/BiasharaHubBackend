package com.biasharahub.repository;

import com.biasharahub.entity.Expense;
import com.biasharahub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByCreatedByOrderByExpenseDateDesc(User user);

    @Query("SELECT e FROM Expense e WHERE e.createdBy.businessId = :businessId ORDER BY e.expenseDate DESC, e.createdAt DESC")
    List<Expense> findByBusinessIdOrderByExpenseDateDesc(@Param("businessId") UUID businessId);

    @Query("SELECT e FROM Expense e WHERE e.createdBy.businessId = :businessId AND e.expenseDate BETWEEN :from AND :to ORDER BY e.expenseDate ASC")
    List<Expense> findByBusinessIdAndDateRange(@Param("businessId") UUID businessId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.createdBy.businessId = :businessId AND e.expenseDate BETWEEN :from AND :to")
    BigDecimal sumAmountByBusinessIdAndDateRange(@Param("businessId") UUID businessId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);
}
