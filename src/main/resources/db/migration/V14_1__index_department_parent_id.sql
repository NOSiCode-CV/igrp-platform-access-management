-- V14_1: Supports the recursive CTE used by DepartmentScopeService to compute
-- a user's subtree scope (see plans/department-scoped-management/plan.md).
-- Partial index — parent_id IS NULL is the root-department set (small); the
-- recursive walk only ever joins on non-null parent_id.

CREATE INDEX IF NOT EXISTS idx_department_parent_id
    ON t_department (parent_id)
    WHERE parent_id IS NOT NULL;
