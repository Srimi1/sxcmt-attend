-- Hardening after security review (CASE-2026-ATTEND-ROUTINE).
-- H1 null-safe validation, H2 size caps, M2/M3 per-user time-window quotas that survive data deletion,
-- M4 create_class checks, M1 owner can rotate the join code, L5 invisible characters rejected,
-- L7 history returned with a by_me flag instead of raw user ids.

CREATE INDEX IF NOT EXISTS classes_created_by_idx ON public.classes(created_by);
CREATE INDEX IF NOT EXISTS routine_versions_created_by_idx ON public.routine_versions(created_by);

-- Quota ledger. Kept when a user deletes their class data so limits cannot be reset;
-- rows are removed with the auth account and pruned after 7 days.
CREATE TABLE public.routine_quota_events (
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  action text NOT NULL CHECK (action IN ('create', 'join', 'join_failed', 'publish')),
  class_id uuid,
  at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX routine_quota_events_user_idx ON public.routine_quota_events(user_id, action, at);
ALTER TABLE public.routine_quota_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.routine_quota_events FROM anon, authenticated;

-- Text shown to classmates: no control or invisible/bidi formatting characters.
CREATE OR REPLACE FUNCTION public.clean_text(p text, p_max integer)
RETURNS boolean
LANGUAGE sql IMMUTABLE
SET search_path = pg_catalog, public, pg_temp
AS $$
  SELECT p IS NOT NULL AND char_length(p) <= p_max AND octet_length(p) <= p_max * 4
    AND p !~ '[[:cntrl:]]' AND p !~ '[­؜᠎​-‏‪-‮⁠-⁯﻿￹-￻]';
$$;

-- Null-safe: any missing key, wrong type or NULL makes the routine invalid.
CREATE OR REPLACE FUNCTION public.valid_routine(p_sessions jsonb)
RETURNS boolean
LANGUAGE plpgsql IMMUTABLE
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE s jsonb;
BEGIN
  IF p_sessions IS NULL OR jsonb_typeof(p_sessions) IS DISTINCT FROM 'array' THEN RETURN false; END IF;
  IF octet_length(p_sessions::text) > 32768 OR jsonb_array_length(p_sessions) NOT BETWEEN 1 AND 72 THEN RETURN false; END IF;
  FOR s IN SELECT * FROM jsonb_array_elements(p_sessions) LOOP
    IF jsonb_typeof(s) IS DISTINCT FROM 'object' THEN RETURN false; END IF;
    IF NOT (s ?& array['id', 'day', 'period', 'subject', 'title', 'time'])
       OR (SELECT count(*) FROM jsonb_object_keys(s)) <> 6 THEN RETURN false; END IF;
    IF NOT coalesce(
         jsonb_typeof(s->'id') = 'string' AND (s->>'id') ~ '^[A-Za-z0-9-]{1,64}$'
         AND jsonb_typeof(s->'day') = 'number' AND (s->>'day') ~ '^[1-6]$'
         AND jsonb_typeof(s->'period') = 'number' AND (s->>'period') ~ '^([1-9]|1[0-2])$'
         AND jsonb_typeof(s->'subject') = 'string' AND (s->>'subject') ~ '^[A-Z0-9_]{1,20}$'
         AND jsonb_typeof(s->'title') = 'string' AND public.clean_text(s->>'title', 80) AND btrim(s->>'title') <> ''
         AND jsonb_typeof(s->'time') = 'string' AND public.clean_text(s->>'time', 30),
       false) THEN
      RETURN false;
    END IF;
  END LOOP;
  RETURN (SELECT count(DISTINCT x->>'id') = count(*) AND count(DISTINCT (x->>'day') || '-' || (x->>'period')) = count(*)
          FROM jsonb_array_elements(p_sessions) x);
END;
$$;

CREATE OR REPLACE FUNCTION public.quota_check(p_action text, p_window interval, p_limit integer, p_message text)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  IF (SELECT count(*) FROM public.routine_quota_events
      WHERE user_id = auth.uid() AND action = p_action AND at > now() - p_window) >= p_limit THEN
    RAISE EXCEPTION '%', p_message;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.quota_note(p_action text, p_class uuid)
RETURNS void
LANGUAGE sql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
  DELETE FROM public.routine_quota_events WHERE user_id = auth.uid() AND at < now() - interval '7 days';
  INSERT INTO public.routine_quota_events(user_id, action, class_id) VALUES (auth.uid(), p_action, p_class);
$$;

CREATE OR REPLACE FUNCTION public.drop_empty_classes()
RETURNS void
LANGUAGE sql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
  DELETE FROM public.classes c WHERE NOT EXISTS (SELECT 1 FROM public.class_members m WHERE m.class_id = c.id);
$$;

CREATE OR REPLACE FUNCTION public.create_class(p_name text, p_sessions jsonb, p_effective_from date)
RETURNS public.classes
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE me uuid := auth.uid(); created public.classes;
BEGIN
  IF me IS NULL THEN RAISE EXCEPTION 'Sign in first' USING ERRCODE = '42501'; END IF;
  PERFORM pg_advisory_xact_lock(hashtext('routine-user:' || me::text));
  IF NOT coalesce(public.clean_text(p_name, 80) AND char_length(btrim(p_name)) >= 3, false) THEN RAISE EXCEPTION 'Class name must be 3 to 80 characters'; END IF;
  IF p_effective_from IS NULL OR p_effective_from NOT BETWEEN current_date - 60 AND current_date + 365 THEN
    RAISE EXCEPTION 'Effective date must be within 60 days back or a year ahead';
  END IF;
  IF NOT public.valid_routine(p_sessions) THEN RAISE EXCEPTION 'Invalid timetable'; END IF;
  PERFORM public.quota_check('create', interval '1 day', 3, 'You can create at most 3 classes a day');
  IF (SELECT count(*) FROM public.class_members WHERE user_id = me) >= 10 THEN RAISE EXCEPTION 'You can join at most 10 classes'; END IF;
  LOOP
    BEGIN
      INSERT INTO public.classes(name, join_code, created_by) VALUES (btrim(p_name), public.new_join_code(), me) RETURNING * INTO created;
      EXIT;
    EXCEPTION WHEN unique_violation THEN
    END;
  END LOOP;
  INSERT INTO public.class_members(class_id, user_id) VALUES (created.id, me);
  INSERT INTO public.routine_versions(class_id, version, effective_from, sessions, note, created_by)
    VALUES (created.id, 1, p_effective_from, p_sessions, 'First version', me);
  PERFORM public.quota_note('create', created.id);
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
  PERFORM pg_advisory_xact_lock(hashtext('routine-user:' || me::text));
  PERFORM public.quota_check('join_failed', interval '1 hour', 10, 'Too many wrong codes. Try again in an hour');
  SELECT * INTO found FROM public.classes WHERE join_code = upper(btrim(coalesce(p_code, '')));
  IF found.id IS NULL THEN
    PERFORM public.quota_note('join_failed', NULL);
    RETURN NULL; -- the client treats an empty result as "no class has that code"; failures stay recorded
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.class_members WHERE class_id = found.id AND user_id = me) THEN
    PERFORM public.quota_check('join', interval '1 day', 10, 'You can join at most 10 classes a day');
    IF (SELECT count(*) FROM public.class_members WHERE user_id = me) >= 10 THEN RAISE EXCEPTION 'You can join at most 10 classes'; END IF;
    INSERT INTO public.class_members(class_id, user_id) VALUES (found.id, me);
    PERFORM public.quota_note('join', found.id);
  END IF;
  RETURN found;
END;
$$;

CREATE OR REPLACE FUNCTION public.leave_class(p_class uuid)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  DELETE FROM public.class_members WHERE class_id = p_class AND user_id = auth.uid();
  DELETE FROM public.classes c WHERE c.id = p_class AND NOT EXISTS (SELECT 1 FROM public.class_members m WHERE m.class_id = c.id);
END;
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
  IF NOT public.clean_text(coalesce(p_note, ''), 200) THEN RAISE EXCEPTION 'Note is too long or has unsupported characters'; END IF;
  IF p_effective_from IS NULL OR p_effective_from NOT BETWEEN current_date - 60 AND current_date + 365 THEN
    RAISE EXCEPTION 'Effective date must be within 60 days back or a year ahead';
  END IF;
  PERFORM 1 FROM public.classes WHERE id = p_class FOR UPDATE; -- serialise version numbers and the class limit
  IF (SELECT count(*) FROM public.routine_quota_events WHERE user_id = me AND action = 'publish' AND class_id = p_class AND at > now() - interval '1 day') >= 5 THEN
    RAISE EXCEPTION 'You can publish at most 5 routine changes a day';
  END IF;
  IF (SELECT count(*) FROM public.routine_versions WHERE class_id = p_class AND created_at > now() - interval '1 day') >= 30 THEN
    RAISE EXCEPTION 'Too many routine changes today';
  END IF;
  SELECT coalesce(max(version), 0) + 1 INTO next_version FROM public.routine_versions WHERE class_id = p_class;
  INSERT INTO public.routine_versions(class_id, version, effective_from, sessions, note, created_by)
    VALUES (p_class, next_version, p_effective_from, p_sessions, coalesce(p_note, ''), me) RETURNING * INTO created;
  PERFORM public.quota_note('publish', p_class);
  RETURN created;
END;
$$;

-- Owner-only: invalidate a leaked join code. Existing members stay.
CREATE OR REPLACE FUNCTION public.rotate_join_code(p_class uuid)
RETURNS public.classes
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE updated public.classes;
BEGIN
  LOOP
    BEGIN
      UPDATE public.classes SET join_code = public.new_join_code()
        WHERE id = p_class AND created_by = auth.uid() AND auth.uid() IS NOT NULL RETURNING * INTO updated;
      EXIT;
    EXCEPTION WHEN unique_violation THEN
    END;
  END LOOP;
  IF updated.id IS NULL THEN RAISE EXCEPTION 'Only the class creator can change the join code' USING ERRCODE = '42501'; END IF;
  RETURN updated;
END;
$$;

-- Latest versions for a member, newest first, with only a by_me flag about the author.
CREATE OR REPLACE FUNCTION public.routine_history(p_class uuid, p_limit integer DEFAULT 10)
RETURNS TABLE (version integer, effective_from date, sessions jsonb, note text, by_me boolean, created_at timestamptz, is_owner boolean)
LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  IF auth.uid() IS NULL OR NOT public.is_class_member(p_class) THEN RAISE EXCEPTION 'Join this class first' USING ERRCODE = '42501'; END IF;
  RETURN QUERY
    SELECT v.version, v.effective_from, v.sessions, v.note, v.created_by IS NOT DISTINCT FROM auth.uid() AND v.created_by IS NOT NULL,
           v.created_at, (SELECT c.created_by = auth.uid() FROM public.classes c WHERE c.id = p_class)
    FROM public.routine_versions v WHERE v.class_id = p_class
    ORDER BY v.version DESC LIMIT least(greatest(coalesce(p_limit, 10), 1), 20);
END;
$$;

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
  PERFORM public.drop_empty_classes();
END;
$$;

-- Clients read history only through routine_history (no raw author ids).
REVOKE SELECT ON public.routine_versions FROM authenticated;

REVOKE ALL ON FUNCTION public.clean_text(text, integer), public.quota_check(text, interval, integer, text), public.quota_note(text, uuid),
  public.drop_empty_classes(), public.rotate_join_code(uuid), public.routine_history(uuid, integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.clean_text(text, integer), public.quota_check(text, interval, integer, text), public.quota_note(text, uuid),
  public.drop_empty_classes() FROM authenticated;
GRANT EXECUTE ON FUNCTION public.rotate_join_code(uuid), public.routine_history(uuid, integer) TO authenticated;
