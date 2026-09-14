"""Certbot manual DNS-01 authentication and cleanup entry point."""

import argparse
import ipaddress
import json
import os
import re
import sys

from .propagation import wait_for_txt
from .ambiguous_write_error import AmbiguousWriteError
from .yandex_dns import DnsError, YandexDns, private_json, validation_present

RECONCILIATION_MARKER = "/etc/letsencrypt/.commonex-dns-reconciliation-required"


def reconciliation_marker(config):
    return config.get("reconciliation_marker", RECONCILIATION_MARKER)


def mark_reconciliation(config, name, token, reason):
    path = reconciliation_marker(config)
    payload = {"zone_id": config["zone_id"], "name": name, "token": token, "reason": reason}
    try:
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        return
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        json.dump(payload, stream)
        stream.flush()
        os.fsync(stream.fileno())
    if os.name == 'posix':
        directory = os.open(os.path.dirname(path) or '.', os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)


def reconciliation_required(config):
    return DnsError("DNS reconciliation required; inspect " + str(reconciliation_marker(config)))

def domain_name(value):
    value = value.lower().rstrip(".")
    if value.startswith("*."):
        value = value[2:]
    try:
        ipaddress.ip_address(value)
    except ValueError:
        pass
    else:
        raise DnsError("DNS-01 does not support IP identifiers")
    if (len(value) > 253 or "." not in value or any(
            not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", label)
            for label in value.split("."))):
        raise DnsError("Invalid DNS identifier")
    return value


def challenge(config, environment):
    zone = domain_name(config["zone_name"]) + "."
    identifier = domain_name(environment.get("CERTBOT_IDENTIFIER") or
                             environment.get("CERTBOT_DOMAIN", ""))
    allowed = {domain_name(value) for value in config["allowed_identifiers"]}
    if identifier not in allowed or not (identifier + ".").endswith("." + zone) and identifier + "." != zone:
        raise DnsError("Challenge identifier is outside the configured allowlist or zone")
    token = environment.get("CERTBOT_VALIDATION", "")
    if not re.fullmatch(r"[A-Za-z0-9_-]{43}", token):
        raise DnsError("Invalid DNS-01 validation value")
    timeout = config.get("propagation_timeout_seconds", 180)
    if isinstance(timeout, bool) or not isinstance(timeout, int) or not 1 <= timeout <= 1800:
        raise DnsError("Propagation timeout must be 1 to 1800 seconds")
    return zone, "_acme-challenge." + identifier + ".", token, timeout


def run_hook(action, config, environment, api, propagate=wait_for_txt, output=None):
    output = output or sys.stdout
    zone, name, token, timeout = challenge(config, environment)
    if action == "auth" and os.path.lexists(reconciliation_marker(config)):
        raise reconciliation_required(config)
    if action == "cleanup":
        receipt_text = environment.get("CERTBOT_AUTH_OUTPUT", "").strip()
        if not receipt_text:
            return
        try:
            receipt = json.loads(receipt_text)
        except ValueError:
            raise DnsError("Invalid authentication receipt; refusing cleanup") from None
        if (not isinstance(receipt, dict) or receipt.get("name") != name or
                receipt.get("token") != token or receipt.get("zone_id") != config["zone_id"] or
                type(receipt.get("owned")) is not bool):
            raise DnsError("Authentication receipt mismatch; refusing cleanup")
        if receipt["owned"]:
            try:
                api.verify_zone(zone)
                api.change_value(name, token, add=False)
            except Exception:
                mark_reconciliation(config, name, token, "cleanup_failed")
                raise reconciliation_required(config) from None
        return
    api.verify_zone(zone)
    before = api.get_record(name)
    owned = not validation_present(before, token)
    # Emit before writing: Certbot can pass this receipt to cleanup even after
    # a failed auth hook. This is never a receipt for unrelated/preexisting data.
    print(json.dumps({"zone_id": config["zone_id"], "name": name,
                      "token": token, "owned": owned}), file=output, flush=True)
    added = False
    try:
        if owned:
            api.change_value(name, token, add=True, initial=before)
            added = True
        propagate(zone, name, token, timeout)
    except Exception as error:
        uncertain = isinstance(error, AmbiguousWriteError)
        if uncertain:
            mark_reconciliation(config, name, token, "write_completion_uncertain")
        if owned and (added or uncertain or getattr(api, "mutation_started", False)):
            try:
                api.change_value(name, token, add=False)
            except Exception:
                mark_reconciliation(config, name, token, "cleanup_failed")
                raise reconciliation_required(config) from None
        if uncertain:
            raise reconciliation_required(config) from None
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("auth", "cleanup"))
    parser.add_argument("--config", required=True)
    args = parser.parse_args()
    try:
        config = private_json(args.config)
        api = YandexDns(config["zone_id"], config["service_account_key"])
        run_hook(args.action, config, os.environ, api)
    except Exception as error:
        message = str(error) if isinstance(error, DnsError) else "Invalid certificate hook configuration or runtime input"
        print(message, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
