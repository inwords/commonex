"""Exercise publication policy without real network access or optional modules."""

import sys
import types
import unittest
from unittest.mock import Mock, patch

from commonex_certificates.propagation import wait_for_txt
from commonex_certificates.yandex_dns import DnsError


class DnsException(Exception):
    pass


class NoAnswer(DnsException):
    pass


class PropagationTests(unittest.TestCase):
    def setUp(self):
        self.now = 0
        self.name = "_acme-challenge.example.com."
        self.query = types.SimpleNamespace(flags=256, question=[types.SimpleNamespace(name=self.name)])
        self.modules = {"dns": types.ModuleType("dns")}
        for name in ("exception", "flags", "message", "query", "rcode", "rdatatype", "resolver"):
            module = types.ModuleType("dns." + name)
            self.modules["dns." + name] = module
            setattr(self.modules["dns"], name, module)
        dns = self.modules["dns"]
        dns.exception.DNSException = DnsException
        dns.flags.AA, dns.flags.RD = 1024, 256
        dns.rcode.NOERROR, dns.rdatatype.TXT = 0, 16
        dns.message.make_query = Mock(return_value=self.query)
        dns.query.udp_with_fallback = Mock()
        dns.resolver.NoAnswer = NoAnswer
        dns.resolver.NXDOMAIN = DnsException
        dns.resolver.Resolver = Mock(return_value=types.SimpleNamespace(resolve=self.resolve))
        self.dns = dns
        self.patchers = [patch.dict(sys.modules, self.modules),
                         patch("commonex_certificates.propagation.time.monotonic", side_effect=lambda: self.now),
                         patch("commonex_certificates.propagation.time.sleep", side_effect=self.sleep)]
        for patcher in self.patchers:
            patcher.start()
            self.addCleanup(patcher.stop)

    def sleep(self, seconds):
        self.now += seconds

    def resolve(self, name, kind, lifetime):
        if kind == "NS":
            return [types.SimpleNamespace(target=types.SimpleNamespace(to_text=lambda n=n: n))
                    for n in ("ns1.example.com.", "ns2.example.com.")]
        if kind == "AAAA":
            raise NoAnswer()
        return ["192.0.2.1" if name.startswith("ns1") else "192.0.2.2"]

    def answer(self, token=b"a" * 43, authoritative=True, owner=None):
        class Rrset(list):
            pass
        rrset = Rrset([types.SimpleNamespace(strings=(token,))])
        rrset.name = owner or self.name
        rrset.rdtype = 16
        return types.SimpleNamespace(flags=1024 if authoritative else 0,
                                     rcode=lambda: 0, answer=[rrset]), False

    def test_waits_for_every_authoritative_server(self):
        self.dns.query.udp_with_fallback.side_effect = [
            self.answer(), self.answer(b"wrong"), self.answer(), self.answer()]
        wait_for_txt("example.com.", self.name, "a" * 43, 10)
        addresses = [call.args[1] for call in self.dns.query.udp_with_fallback.call_args_list]
        self.assertEqual(addresses, ["192.0.2.1", "192.0.2.2"] * 2)
        self.assertEqual(self.query.flags & 256, 0)
        self.assertGreater(self.now, 0)

    def test_rejects_recursive_answers_even_with_matching_token(self):
        self.dns.query.udp_with_fallback.return_value = self.answer(authoritative=False)
        with self.assertRaisesRegex(DnsError, "did not propagate"):
            wait_for_txt("example.com.", self.name, "a" * 43, 5)
        self.assertEqual(self.now, 5)

    def test_rejects_matching_token_at_another_owner(self):
        self.dns.query.udp_with_fallback.return_value = self.answer(owner="other.example.com.")
        with self.assertRaises(DnsError):
            wait_for_txt("example.com.", self.name, "a" * 43, 3)

    def test_accepts_segmented_txt_wire_data(self):
        answer, tcp = self.answer()
        answer.answer[0][0].strings = (b"a" * 20, b"a" * 23)
        self.dns.query.udp_with_fallback.return_value = (answer, True)
        wait_for_txt("example.com.", self.name, "a" * 43, 3)
        self.assertEqual(self.dns.query.udp_with_fallback.call_count, 2)


if __name__ == "__main__":
    unittest.main()
