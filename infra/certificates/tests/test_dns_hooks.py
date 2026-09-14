import copy
import io
import json
import os
import tempfile
import unittest
from unittest.mock import Mock, patch
from urllib.error import URLError

from commonex_certificates.dns_hook import challenge, run_hook
from commonex_certificates.yandex_dns import ApiError, DnsError, YandexDns
from commonex_certificates.ambiguous_write_error import AmbiguousWriteError


TOKEN = "a" * 43
OTHER = "b" * 43
NAME = "_acme-challenge.example.com."
CONFIG = {"zone_id": "test-zone", "zone_name": "example.com.",
          "allowed_identifiers": ["example.com", "*.example.com"]}
ENV = {"CERTBOT_IDENTIFIER": "example.com", "CERTBOT_VALIDATION": TOKEN}


class MemoryDns(YandexDns):
    def __init__(self, record=None):
        super().__init__("test-zone", "unused", sleep=lambda _: None)
        self.record = copy.deepcopy(record)
        self.writes = []
        self.conflict = None

    def verify_zone(self, name):
        if name != "example.com.":
            raise DnsError("Zone mismatch")

    def get_record(self, name):
        return copy.deepcopy(self.record)

    def replace(self, before, after):
        if self.conflict:
            self.record = self.conflict
            self.conflict = None
            raise ApiError(9)
        if before != self.record:
            raise ApiError(10)
        self.writes.append((before, after))
        self.record = copy.deepcopy(after)


def record(*values):
    return {"name": NAME, "type": "TXT", "ttl": "321",
            "description": "Do not replace this metadata", "data": list(values)}


class DnsMutationTests(unittest.TestCase):
    def test_auth_and_cleanup_preserve_unrelated_txt_ttl_description(self):
        existing = record('"unrelated"')
        api = MemoryDns(existing)
        output = io.StringIO()
        run_hook("auth", CONFIG, ENV, api, propagate=lambda *args: None, output=output)
        self.assertEqual(api.record, record('"unrelated"', '"' + TOKEN + '"'))
        run_hook("cleanup", CONFIG, dict(ENV, CERTBOT_AUTH_OUTPUT=output.getvalue()), api)
        self.assertEqual(api.record, existing)

    def test_apex_and_wildcard_keep_both_values_until_their_own_cleanup(self):
        api = MemoryDns()
        apex_output, wildcard_output = io.StringIO(), io.StringIO()
        wildcard_env = dict(ENV, CERTBOT_IDENTIFIER="*.example.com", CERTBOT_VALIDATION=OTHER)
        run_hook("auth", CONFIG, ENV, api, propagate=lambda *args: None, output=apex_output)
        run_hook("auth", CONFIG, wildcard_env, api, propagate=lambda *args: None, output=wildcard_output)
        self.assertEqual(set(api.record["data"]), {'"' + TOKEN + '"', '"' + OTHER + '"'})
        run_hook("cleanup", CONFIG, dict(ENV, CERTBOT_AUTH_OUTPUT=apex_output.getvalue()), api)
        self.assertEqual(api.record["data"], ['"' + OTHER + '"'])
        run_hook("cleanup", CONFIG, dict(wildcard_env, CERTBOT_AUTH_OUTPUT=wildcard_output.getvalue()), api)
        self.assertIsNone(api.record)

    def test_compare_conflict_refetches_and_preserves_other_writer(self):
        api = MemoryDns(record('"old"'))
        api.conflict = record('"old"', '"concurrent"')
        api.change_value(NAME, TOKEN, add=True)
        self.assertEqual(api.record, record('"old"', '"concurrent"', '"' + TOKEN + '"'))

    def test_propagation_failure_rolls_back_only_owned_token(self):
        api = MemoryDns(record('"unrelated"'))
        def fail(*args):
            raise DnsError("Timed out")
        with self.assertRaisesRegex(DnsError, "Timed out"):
            run_hook("auth", CONFIG, ENV, api, propagate=fail, output=io.StringIO())
        self.assertEqual(api.record, record('"unrelated"'))

    def test_preexisting_token_never_becomes_owned_or_deleted(self):
        existing = record('"' + TOKEN + '"')
        api, output = MemoryDns(existing), io.StringIO()
        run_hook("auth", CONFIG, ENV, api, propagate=lambda *args: None, output=output)
        self.assertFalse(json.loads(output.getvalue())["owned"])
        run_hook("cleanup", CONFIG, dict(ENV, CERTBOT_AUTH_OUTPUT=output.getvalue()), api)
        self.assertEqual(api.record, existing)
        self.assertEqual(api.writes, [])

    def test_preexisting_segmented_txt_is_recognized_without_rewriting(self):
        existing = record('"' + TOKEN[:20] + '" "' + TOKEN[20:] + '"')
        api, output = MemoryDns(existing), io.StringIO()
        run_hook("auth", CONFIG, ENV, api, propagate=lambda *args: None, output=output)
        self.assertFalse(json.loads(output.getvalue())["owned"])
        self.assertEqual(api.record, existing)
        self.assertEqual(api.writes, [])

    def test_cleanup_is_idempotent_when_token_already_absent(self):
        api = MemoryDns(record('"unrelated"'))
        receipt = json.dumps({"name": NAME, "zone_id": "test-zone", "token": TOKEN, "owned": True})
        run_hook("cleanup", CONFIG, dict(ENV, CERTBOT_AUTH_OUTPUT=receipt), api)
        self.assertEqual(api.writes, [])

    def test_cleanup_rejects_receipt_for_another_challenge(self):
        api = MemoryDns(record('"' + TOKEN + '"'))
        receipt = json.dumps({"name": NAME, "zone_id": "test-zone", "token": OTHER, "owned": True})
        with self.assertRaises(DnsError):
            run_hook("cleanup", CONFIG, dict(ENV, CERTBOT_AUTH_OUTPUT=receipt), api)
        self.assertEqual(api.writes, [])

    def test_rejects_unlisted_domain_and_suffix_trick(self):
        for identifier in ("other.example.com", "badexample.com", "example.com.evil.test", "127.0.0.1"):
            with self.subTest(identifier=identifier), self.assertRaises(DnsError):
                challenge(CONFIG, dict(ENV, CERTBOT_IDENTIFIER=identifier))

    def test_supports_legacy_certbot_domain(self):
        result = challenge(CONFIG, {"CERTBOT_DOMAIN": "example.com", "CERTBOT_VALIDATION": TOKEN})
        self.assertEqual(result[1], NAME)


class ApiOperationTests(unittest.TestCase):
    def test_mutation_transport_failure_is_explicitly_uncertain(self):
        api = YandexDns("zone", "unused")
        api.token = "test-token"
        with patch("commonex_certificates.yandex_dns.urlopen", side_effect=URLError("offline")):
            with self.assertRaises(AmbiguousWriteError):
                api.replace(None, record(TOKEN))
        self.assertTrue(api.mutation_started)

    def test_read_transport_failure_does_not_start_mutation(self):
        api = YandexDns("zone", "unused")
        api.token = "test-token"
        with patch("commonex_certificates.yandex_dns.urlopen", side_effect=URLError("offline")):
            with self.assertRaises(DnsError) as raised:
                api.get_record(NAME)
        self.assertNotIsInstance(raised.exception, AmbiguousWriteError)
        self.assertFalse(api.mutation_started)

    def test_lost_operation_poll_is_uncertain(self):
        api = YandexDns("zone", "unused", sleep=lambda _: None)
        with patch.object(api, "_request", side_effect=[{"id": "op", "done": False}, DnsError("offline")]):
            with self.assertRaises(AmbiguousWriteError):
                api.replace(None, record(TOKEN))

    def test_waits_for_asynchronous_operation(self):
        api = YandexDns("zone", "unused", sleep=lambda _: None)
        with patch.object(api, "_request", side_effect=[{"id": "op", "done": False},
                                                       {"id": "op", "done": True, "response": {}}]) as request:
            api.replace(None, record('"' + TOKEN + '"'))
        self.assertEqual(request.call_args.args, ("GET", "https://operation.api.cloud.yandex.net/operations/op"))

    def test_http_success_with_operation_error_is_failure(self):
        api = YandexDns("zone", "unused", sleep=lambda _: None)
        with patch.object(api, "_request", return_value={"done": True, "error": {"code": 7}}):
            with self.assertRaises(ApiError) as raised:
                api.replace(None, record(TOKEN))
        self.assertEqual(raised.exception.code, 7)

    def test_pending_operation_has_deadline(self):
        api = YandexDns("zone", "unused", clock=iter([0, 121]).__next__, sleep=lambda _: None)
        with patch.object(api, "_request", return_value={"id": "op", "done": False}):
            with self.assertRaisesRegex(DnsError, "uncertain"):
                api.replace(None, record(TOKEN))

    def test_permission_error_is_not_retried_as_conflict(self):
        api = MemoryDns()
        with patch.object(api, "replace", side_effect=ApiError(7)) as replace:
            with self.assertRaises(ApiError):
                api.change_value(NAME, TOKEN, add=True)
        self.assertEqual(replace.call_count, 1)


class ReconciliationTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.marker = os.path.join(temporary.name, "reconciliation-required")
        self.config = dict(CONFIG, reconciliation_marker=self.marker)

    def test_uncertain_write_blocks_retry_even_when_cleanup_looks_successful(self):
        api = MemoryDns()
        with patch.object(api, "change_value", side_effect=[AmbiguousWriteError("uncertain"), None]):
            with self.assertRaisesRegex(DnsError, "reconciliation required"):
                run_hook("auth", self.config, ENV, api, output=io.StringIO())
        with open(self.marker, encoding="utf-8") as stream:
            self.assertEqual(json.load(stream), {"zone_id": "test-zone", "name": NAME,
                                                "token": TOKEN, "reason": "write_completion_uncertain"})
        if os.name == "posix":
            self.assertEqual(os.stat(self.marker).st_mode & 0o777, 0o600)

    def test_cleanup_failure_sets_marker(self):
        api = MemoryDns(record('"' + TOKEN + '"'))
        receipt = json.dumps({"zone_id": "test-zone", "name": NAME, "token": TOKEN, "owned": True})
        with patch.object(api, "change_value", side_effect=ApiError(7)):
            with self.assertRaisesRegex(DnsError, "reconciliation required"):
                run_hook("cleanup", self.config, dict(ENV, CERTBOT_AUTH_OUTPUT=receipt), api)
        self.assertTrue(os.path.isfile(self.marker))

    def test_existing_marker_refuses_auth_before_api_access(self):
        with open(self.marker, "w", encoding="utf-8") as stream:
            stream.write("existing")
        api = MemoryDns()
        with patch.object(api, "verify_zone") as verify:
            with self.assertRaisesRegex(DnsError, "reconciliation required"):
                run_hook("auth", self.config, ENV, api, output=io.StringIO())
        verify.assert_not_called()

    def test_cleanup_still_works_and_never_clears_marker(self):
        with open(self.marker, "w", encoding="utf-8") as stream:
            stream.write("existing")
        api = MemoryDns(record('"unrelated"', '"' + TOKEN + '"'))
        receipt = json.dumps({"zone_id": "test-zone", "name": NAME, "token": TOKEN, "owned": True})
        run_hook("cleanup", self.config, dict(ENV, CERTBOT_AUTH_OUTPUT=receipt), api)
        self.assertEqual(api.record, record('"unrelated"'))
        with open(self.marker, encoding="utf-8") as stream:
            self.assertEqual(stream.read(), "existing")

    def test_authentication_read_failure_does_not_require_reconciliation(self):
        api = MemoryDns()
        with patch.object(api, "verify_zone", side_effect=DnsError("network failure")):
            with self.assertRaisesRegex(DnsError, "network failure"):
                run_hook("auth", self.config, ENV, api, output=io.StringIO())
        self.assertFalse(os.path.exists(self.marker))

    def test_failed_rollback_after_propagation_sets_marker(self):
        api = MemoryDns()
        with patch.object(api, "change_value", side_effect=[None, ApiError(7)]):
            with self.assertRaisesRegex(DnsError, "reconciliation required"):
                run_hook("auth", self.config, ENV, api,
                         propagate=Mock(side_effect=DnsError("propagation failure")), output=io.StringIO())
        self.assertTrue(os.path.isfile(self.marker))


if __name__ == "__main__":
    unittest.main()
