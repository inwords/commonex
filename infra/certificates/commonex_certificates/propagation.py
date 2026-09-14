"""Check TXT publication at every authoritative nameserver."""

import time

from .yandex_dns import DnsError


def wait_for_txt(zone, name, token, timeout=180):
    import dns.exception
    import dns.flags
    import dns.message
    import dns.query
    import dns.rcode
    import dns.rdatatype
    import dns.resolver

    deadline = time.monotonic() + timeout
    resolver = dns.resolver.Resolver()

    def resolve(owner, kind):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise DnsError("Authoritative DNS discovery timed out")
        return resolver.resolve(owner, kind, lifetime=min(10, remaining))

    try:
        servers = [record.target.to_text() for record in resolve(zone, "NS")]
        addresses = {}
        for server in servers:
            addresses[server] = []
            for kind in ("A", "AAAA"):
                try:
                    addresses[server].extend(str(record) for record in resolve(server, kind))
                except (dns.resolver.NoAnswer, dns.resolver.NXDOMAIN):
                    # A nameserver may support only one address family; require either below.
                    pass
            if not addresses[server]:
                raise DnsError("An authoritative nameserver has no address")
    except dns.exception.DNSException:
        raise DnsError("Could not discover authoritative DNS servers") from None
    if not servers:
        raise DnsError("No authoritative DNS servers found")
    query = dns.message.make_query(name, "TXT")
    query.flags &= ~dns.flags.RD
    while time.monotonic() < deadline:
        all_ready = True
        for server in servers:
            server_ready = False
            for address in addresses[server]:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    break
                try:
                    response, _ = dns.query.udp_with_fallback(
                        query, address, timeout=min(3, remaining))
                    if not (response.flags & dns.flags.AA) or response.rcode() != dns.rcode.NOERROR:
                        continue
                    server_ready = any(
                        b"".join(record.strings) == token.encode("ascii")
                        for rrset in response.answer
                        if rrset.name == query.question[0].name and rrset.rdtype == dns.rdatatype.TXT
                        for record in rrset)
                    if server_ready:
                        break
                except (dns.exception.DNSException, OSError):
                    continue
            all_ready = all_ready and server_ready
        if all_ready:
            return
        time.sleep(min(3, max(0, deadline - time.monotonic())))
    raise DnsError("Challenge TXT did not propagate to all authoritative nameservers")
