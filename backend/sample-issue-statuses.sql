BEGIN;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM issues) < 3 THEN
        RAISE EXCEPTION 'Create at least three issue reports before running this sample update.';
    END IF;
END
$$;

WITH selected AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS row_number
    FROM issues
    ORDER BY id
    LIMIT 3
),
updated AS (
    UPDATE issues AS issue
    SET status = CASE selected.row_number
            WHEN 1 THEN 'PENDING'
            WHEN 2 THEN 'IN_PROGRESS'
            ELSE 'RESOLVED'
        END,
        priority = CASE selected.row_number
            WHEN 1 THEN 'HIGH'
            WHEN 2 THEN 'MEDIUM'
            ELSE 'LOW'
        END
    FROM selected
    WHERE issue.id = selected.id
    RETURNING issue.id, issue.title, issue.status, issue.priority
)
SELECT id, title, status, priority
FROM updated
ORDER BY id;

COMMIT;
