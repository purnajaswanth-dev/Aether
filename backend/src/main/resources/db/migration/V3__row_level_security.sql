-- V3: Row-Level Security (RLS) on all project-scoped tables

-- Project-scoped tables:
-- project_members, project_integrations, project_permissions, artifacts,
-- artifact_versions, artifact_references, technical_decisions,
-- decision_versions, decision_evidence, timeline_events

-- Create application helper function to check project isolation
CREATE OR REPLACE FUNCTION current_project_matches(target_project_id UUID)
RETURNS BOOLEAN AS $$
BEGIN
    -- If bypass is explicitly set to 'on' (for migrations or unscoped system operations)
    IF current_setting('app.bypass_rls', true) = 'on' THEN
        RETURN true;
    END IF;

    -- Compare against transaction-local app.current_project_id
    RETURN target_project_id = NULLIF(current_setting('app.current_project_id', true), '')::uuid;
END;
$$ LANGUAGE plpgsql STABLE;

-- Macro to enable, force, and add policy
DO $$
DECLARE
    tbl text;
    tables text[] := ARRAY[
        'project_members',
        'project_integrations',
        'project_permissions',
        'artifacts',
        'artifact_versions',
        'artifact_references',
        'technical_decisions',
        'decision_versions',
        'decision_evidence',
        'timeline_events'
    ];
BEGIN
    FOREACH tbl IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY;', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY;', tbl);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I;', tbl || '_isolation_policy', tbl);
        EXECUTE format(
            'CREATE POLICY %I ON %I FOR ALL USING (current_project_matches(project_id)) WITH CHECK (current_project_matches(project_id));',
            tbl || '_isolation_policy', tbl
        );
    END LOOP;
END;
$$;

