from .dns_error import DnsError


class AmbiguousWriteError(DnsError):
    """A DNS mutation may still complete and requires operator reconciliation."""
