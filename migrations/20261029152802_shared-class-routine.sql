-- Shared class routine for Attend.
-- Only timetables live here. Attendance marks, sheet numbers and activity hours stay on each phone.
-- Clients may read rows for classes they belong to; every write goes through the validated functions below.

CREATE TABLE public.classes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL CHECK (char_length(btrim(name)) BETWEEN 3 AND 80),
  join_code text NOT NULL UNIQUE CHECK (join_code ~ '^[A-HJ-NP-Z2-9]{10}$'),
  created_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.class_members (
  class_id uuid NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  joined_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (class_id, user_id)
);
CREATE INDEX class_members_user_idx ON public.class_members(user_id);

-- Append-only: a correction or revert is a new version.
CREATE TABLE public.routine_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  class_id uuid NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
  version integer NOT NULL CHECK (version >= 1),
  effective_from date NOT NULL,
  sessions jsonb NOT NULL,
  note text NOT NULL DEFAULT '' CHECK (char_length(note) <= 200),
  created_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (class_id, version)
);

-- Helpers ------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.is_class_member(p_class uuid)
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
  SELECT EXISTS (SELECT 1 FROM public.class_members m WHERE m.class_id = p_class AND m.user_id = auth.uid());
$$;

-- Mirrors the app's WeekSession rules: Monday-Saturday, periods 1-12, one subject per slot.
CREATE OR REPLACE FUNCTION public.valid_routine(p_sessions jsonb)
RETURNS boolean
LANGUAGE plpgsql IMMUTABLE
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE s jsonb;
BEGIN
  IF jsonb_typeof(p_sessions) <> 'array' OR jsonb_array_length(p_sessions) NOT BETWEEN 1 AND 72 THEN RETURN false; END IF;
  FOR s IN SELECT * FROM jsonb_array_elements(p_sessions) LOOP
    IF jsonb_typeof(s) <> 'object'
       OR (SELECT count(*) FROM jsonb_object_keys(s) k WHERE k NOT IN ('id', 'day', 'period', 'subject', 'title', 'time')) > 0
       OR jsonb_typeof(s->'id') <> 'string' OR char_length(s->>'id') NOT BETWEEN 1 AND 64
       OR jsonb_typeof(s->'day') <> 'number' OR (s->>'day') !~ '^[1-6]$'
       OR jsonb_typeof(s->'period') <> 'number' OR (s->>'period') !~ '^([1-9]|1[0-2])$'
       OR jsonb_typeof(s->'subject') <> 'string' OR (s->>'subject') !~ '^[A-Z0-9_]{1,20}$'
       OR jsonb_typeof(s->'title') <> 'string' OR char_length(btrim(s->>'title')) NOT BETWEEN 1 AND 80
       OR jsonb_typeof(s->'time') <> 'string' OR char_length(s->>'time') > 30 THEN
      RETURN false;
    END IF;
  END LOOP;
  RETURN (SELECT count(DISTINCT x->>'id') = count(*) AND count(DISTINCT (x->>'day') || '-' || (x->>'period')) = count(*)
          FROM jsonb_array_elements(p_sessions) x);
END;
$$;

CREATE OR REPLACE FUNCTION public.new_join_code()
RETURNS text
LANGUAGE plpgsql VOLATILE
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  bytes bytea := gen_random_bytes(10);
  code text := '';
BEGIN
  FOR i IN 0..9 LOOP code := code || substr(alphabet, (get_byte(bytes, i) % 32) + 1, 1); END LOOP;
  RETURN code;
END;
$$;

-- Client API --------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.create_class(p_name text, p_sessions jsonb, p_effective_from date)
RETURNS public.classes
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE me uuid := auth.uid(); created public.classes;
BEGIN
  IF me IS NULL THEN RAISE EXCEPTION 'Sign in first' USING ERRCODE = '42501'; END IF;
  IF (SELECT count(*) FROM public.classes WHERE created_by = me) >= 5 THEN RAISE EXCEPTION 'You can create at most 5 classes'; END IF;
  IF (SELECT count(*) FROM public.class_members WHERE user_id = me) >= 10 THEN RAISE EXCEPTION 'You can join at most 10 classes'; END IF;
  IF NOT public.valid_routine(p_sessions) THEN RAISE EXCEPTION 'Invalid timetable'; END IF;
  LOOP
    BEGIN
      INSERT INTO public.classes(name, join_code, created_by) VALUES (btrim(p_name), public.new_join_code(), me) RETURNING * INTO created;
      EXIT;
    EXCEPTION WHEN unique_violation THEN -- retry on the rare code collision
    END;
  END LOOP;
  INSERT INTO public.class_members(class_id, user_id) VALUES (created.id, me);
  INSERT INTO public.routine_versions(class_id, version, effective_from, sessions, note, created_by)
    VALUES (created.id, 1, p_effective_from, p_sessions, 'First version', me);
  RETURN created;
END;
$$;

CREATE OR REPLACE FUNCTION public.join_class(p_code text)
RETURNS public.classes
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE me uuid := auth.uid(); found public.classes;
BEGIN
  IF me IS NULL THEN RAISE EXCEPTION 'Sign in first' USING ERRCODE = '42501'; END IF;
  SELECT * INTO found FROM public.classes WHERE join_code = upper(btrim(p_code));
  IF found.id IS NULL THEN RAISE EXCEPTION 'No class has that code'; END IF;
  IF NOT EXISTS (SELECT 1 FROM public.class_members WHERE class_id = found.id AND user_id = me) THEN
    IF (SELECT count(*) FROM public.class_members WHERE user_id = me) >= 10 THEN RAISE EXCEPTION 'You can join at most 10 classes'; END IF;
    INSERT INTO public.class_members(class_id, user_id) VALUES (found.id, me);
  END IF;
  RETURN found;
END;
$$;

CREATE OR REPLACE FUNCTION public.leave_class(p_class uuid)
RETURNS void
LANGUAGE sql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
  DELETE FROM public.class_members WHERE class_id = p_class AND user_id = auth.uid();
$$;

CREATE OR REPLACE FUNCTION public.publish_routine(p_class uuid, p_effective_from date, p_sessions jsonb, p_note text)
RETURNS public.routine_versions
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE me uuid := auth.uid(); next_version integer; created public.routine_versions;
BEGIN
  IF me IS NULL OR NOT public.is_class_member(p_class) THEN RAISE EXCEPTION 'Join this class first' USING ERRCODE = '42501'; END IF;
  IF NOT public.valid_routine(p_sessions) THEN RAISE EXCEPTION 'Invalid timetable'; END IF;
  IF char_length(coalesce(p_note, '')) > 200 THEN RAISE EXCEPTION 'Note is too long'; END IF;
  IF p_effective_from NOT BETWEEN current_date - 60 AND current_date + 365 THEN RAISE EXCEPTION 'Effective date must be within 60 days back or a year ahead'; END IF;
  IF (SELECT count(*) FROM public.routine_versions WHERE class_id = p_class AND created_at > now() - interval '1 day') >= 30 THEN
    RAISE EXCEPTION 'Too many routine changes today';
  END IF;
  PERFORM 1 FROM public.classes WHERE id = p_class FOR UPDATE; -- serialise version numbers
  SELECT coalesce(max(version), 0) + 1 INTO next_version FROM public.routine_versions WHERE class_id = p_class;
  INSERT INTO public.routine_versions(class_id, version, effective_from, sessions, note, created_by)
    VALUES (p_class, next_version, p_effective_from, p_sessions, coalesce(p_note, ''), me) RETURNING * INTO created;
  RETURN created;
END;
$$;

-- Removes the caller from every class and detaches their authorship. Routine history stays for classmates.
CREATE OR REPLACE FUNCTION public.delete_my_class_data()
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE me uuid := auth.uid();
BEGIN
  IF me IS NULL THEN RAISE EXCEPTION 'Sign in first' USING ERRCODE = '42501'; END IF;
  DELETE FROM public.class_members WHERE user_id = me;
  UPDATE public.routine_versions SET created_by = NULL WHERE created_by = me;
  UPDATE public.classes SET created_by = NULL WHERE created_by = me;
END;
$$;

-- Access control ------------------------------------------------------------

ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.class_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.routine_versions ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.classes, public.class_members, public.routine_versions FROM anon, authenticated;
GRANT SELECT ON public.classes, public.class_members, public.routine_versions TO authenticated;

CREATE POLICY "members read their classes" ON public.classes FOR SELECT TO authenticated
  USING (public.is_class_member(id));
CREATE POLICY "members read own memberships" ON public.class_members FOR SELECT TO authenticated
  USING (user_id = (SELECT auth.uid()));
CREATE POLICY "members read routine history" ON public.routine_versions FOR SELECT TO authenticated
  USING (public.is_class_member(class_id));

REVOKE ALL ON FUNCTION public.is_class_member(uuid), public.valid_routine(jsonb), public.new_join_code(),
  public.create_class(text, jsonb, date), public.join_class(text), public.leave_class(uuid),
  public.publish_routine(uuid, date, jsonb, text), public.delete_my_class_data() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_class_member(uuid), public.create_class(text, jsonb, date), public.join_class(text),
  public.leave_class(uuid), public.publish_routine(uuid, date, jsonb, text), public.delete_my_class_data() TO authenticated;
