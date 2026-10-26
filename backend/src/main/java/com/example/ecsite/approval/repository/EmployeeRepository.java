package com.example.ecsite.approval.repository;

import com.example.ecsite.approval.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** 社員と、既存の customer テーブルの名前を ID で結んだ読み取り結果。 */
    interface EmployeeWithName {
        Long getCustomerId();

        String getName();

        String getDepartment();

        String getRole();
    }

    @Query(value = """
            SELECT e.customer_id AS customerId, c.name AS name, e.department AS department, e.role AS role
            FROM employee e JOIN customer c ON c.id = e.customer_id
            WHERE e.customer_id = :customerId
            """, nativeQuery = true)
    Optional<EmployeeWithName> findWithName(long customerId);
}
