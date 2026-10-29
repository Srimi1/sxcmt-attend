-- Quota ledger is server-internal: written only by SECURITY DEFINER functions, never read by clients.
CREATE POLICY "no client access" ON public.routine_quota_events FOR ALL TO anon, authenticated USING (false) WITH CHECK (false);
