"""Narrow Yandex Cloud DNS client with strict record-set updates."""

import json
import os
import re
import stat
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, quote
from urllib.request import Request, urlopen

from .ambiguous_write_error import AmbiguousWriteError
from .api_error import ApiError
from .dns_error import DnsError

IAM_URL = "https://iam.api.cloud.yandex.net/iam/v1/tokens"
DNS_URL = "https://dns.api.cloud.yandex.net/dns/v1/zones/"
OPERATION_URL = "https://operation.api.cloud.yandex.net/operations/"
UNREAD = object()


def private_json(path, require_root=True):
    """Open without following a symlink and validate the actual descriptor."""
    if require_root and hasattr(os, "geteuid") and os.geteuid() != 0:
        raise DnsError("Certificate hooks must run as root")
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    with os.fdopen(descriptor, "r", encoding="utf-8") as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode):
            raise DnsError("Configuration and key must be regular files")
        if os.name == "posix" and (
                info.st_mode & 0o077 or (require_root and info.st_uid != 0)):
            raise DnsError("Configuration and key must be root-owned and private")
        return json.load(stream)


def validation_present(record, token):
    return bool(record and any(_matches_validation(value, token) for value in record["data"]))


def _matches_validation(value, token):
    # TXT presentation may split the same base64url value into quoted chunks.
    # Compare its meaning but retain the original presentation for strict CAS.
    if value == token:
        return True
    if re.fullmatch(r'(?:"[A-Za-z0-9_-]*"\s*)+', value):
        return "".join(re.findall(r'"([A-Za-z0-9_-]*)"', value)) == token
    return False


class YandexDns:
    def __init__(self, zone_id, key_path, clock=time.monotonic, sleep=time.sleep):
        self.zone_url = DNS_URL + quote(zone_id, safe="")
        self.key_path = key_path
        self.clock = clock
        self.sleep = sleep
        self.token = None
        self.mutation_started = False

    def _request(self, method, url, body=None, authenticated=True):
        if authenticated and self.token is None:
            self._authenticate()
        headers = {"Content-Type": "application/json"}
        if authenticated:
            headers["Authorization"] = "Bearer " + self.token
        data = None if body is None else json.dumps(body).encode("utf-8")
        request = Request(url, data=data, headers=headers, method=method)
        mutation = method == "POST" and url.endswith(":updateRecordSets")
        if mutation:
            self.mutation_started = True
        try:
            with urlopen(request, timeout=20) as response:
                return json.load(response)
        except HTTPError as error:
            try:
                code = json.load(error).get("code", error.code)
            except (ValueError, OSError):
                code = error.code
            # Never echo upstream bodies, JWTs, credentials, or request headers.
            if mutation and (error.code >= 500 or code in (2, 4, 13, 14)):
                raise AmbiguousWriteError("DNS write completion is uncertain") from None
            raise ApiError(code) from None
        except (URLError, TimeoutError, OSError, ValueError):
            if mutation:
                raise AmbiguousWriteError("DNS write completion is uncertain") from None
            raise DnsError("Yandex request failed") from None

    def _authenticate(self):
        import jwt

        key = private_json(self.key_path)
        private_key = key["private_key"]
        # yc exports can include a warning before the PEM block.
        begin = private_key.find("-----BEGIN PRIVATE KEY-----")
        if begin < 0:
            raise DnsError("Authorized key does not contain a private-key PEM")
        now = int(time.time())
        signed = jwt.encode(
            {"iss": key["service_account_id"], "aud": IAM_URL,
             "iat": now, "exp": now + 300},
            private_key[begin:], algorithm="PS256", headers={"kid": key["id"]})
        result = self._request("POST", IAM_URL, {"jwt": signed}, authenticated=False)
        self.token = result["iamToken"]

    def verify_zone(self, zone_name):
        zone = self._request("GET", self.zone_url)
        if zone.get("zone", "").lower() != zone_name or "publicVisibility" not in zone:
            raise DnsError("Configured zone must match an actual public Yandex DNS zone")

    def get_record(self, name):
        try:
            return self._request("GET", self.zone_url + ":getRecordSet?" +
                                 urlencode({"name": name, "type": "TXT"}))
        except ApiError as error:
            if error.code in (5, 404):
                return None
            raise

    def replace(self, before, after):
        operation = self._request(
            "POST", self.zone_url + ":updateRecordSets",
            {"deletions": [before] if before else [],
             "additions": [after] if after else []})
        deadline = self.clock() + 120
        while True:
            if "error" in operation:
                raise ApiError(operation["error"].get("code", "unknown"))
            if operation.get("done"):
                return
            if self.clock() >= deadline:
                raise AmbiguousWriteError("DNS operation timed out; completion is uncertain")
            self.sleep(2)
            try:
                operation = self._request("GET", OPERATION_URL +
                                          quote(operation["id"], safe=""))
            except (DnsError, KeyError, TypeError):
                raise AmbiguousWriteError("Could not determine DNS operation completion") from None

    def change_value(self, name, token, add, initial=UNREAD):
        """Retry known compare conflicts, never blindly replay uncertain writes."""
        for attempt in range(6):
            before = initial if attempt == 0 and initial is not UNREAD else self.get_record(name)
            present = validation_present(before, token)
            if present == add:
                return
            if before:
                after = dict(before)
                after["data"] = list(before["data"])
            else:
                after = {"name": name, "type": "TXT", "ttl": "60", "data": []}
            if add:
                after["data"].append('"' + token + '"')
            else:
                after["data"] = [value for value in after["data"]
                                 if not _matches_validation(value, token)]
            try:
                self.replace(before, after if after["data"] else None)
                return
            except ApiError as error:
                if error.code not in (5, 6, 9, 10, 404, 409):
                    raise
                self.sleep(min(attempt + 1, 3))
        raise DnsError("DNS record kept changing during update")
