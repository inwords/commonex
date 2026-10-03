"""Provision or publicly verify the unchanged Android suite's staging fixture."""

import argparse
import base64
import json
import math
from pathlib import Path
import re
import shlex
import ssl
import subprocess
import sys
import urllib.error
import urllib.request


HOST = 'https://staging.commonex.ru'
TARGET = 'commonex@51.250.103.70'
EVENT_ID = '01JYC8BX30EKQYWBRTPKVX6S26'
PERSON_ID = '01JYC8BX30EKQYWBRTPKVX6S27'
PERSON_NAME = 'Test User 2'
REQUIRED_CURRENCIES = {'EUR', 'USD', 'RUB', 'JPY', 'TRY', 'AED'}
APP = Path('/etc/commonex/app')


class FixtureError(ValueError):
    """A diagnostic authored here, safe to display without request credentials."""


def fixture_pin(suite=None):
    # Read the existing test credential rather than adding another secret to source.
    if suite is None:
        suite = Path(__file__).resolve().parents[2] / 'android/app/src/androidTest/kotlin/ru/commonex/BasicInstrumentedTest.kt'
    source = suite.read_text(encoding='utf-8')
    match = re.search(r'fun testJoinExistingEvent\(\).*?\.joinEvent\("' + EVENT_ID
                      + r'", (.*?)\) //', source, re.S)
    parts = re.findall(r'Base64\.decode\("([A-Za-z0-9+/=]+)"\)\.decodeToString\(\)',
                       match.group(1) if match else '')
    if len(parts) != 2:
        raise FixtureError('unchanged Android fixture credential could not be located')
    pin = ''.join(base64.b64decode(part, validate=True).decode('ascii') for part in parts)
    validate_pin(pin)
    return pin


def validate_pin(pin):
    if not isinstance(pin, str) or not re.fullmatch(r'[0-9]{4}', pin):
        raise FixtureError('fixture credential must contain four digits')


def fixture_sql(pin, schema):
    validate_pin(pin)
    if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', schema):
        raise FixtureError('invalid PostgreSQL schema')
    return f'''BEGIN;
SET LOCAL search_path TO "{schema}";
DO $fixture$
DECLARE eur_id varchar;
BEGIN
  SELECT id INTO STRICT eur_id FROM currency WHERE code = 'EUR';
  INSERT INTO event (id, name, currency_id, pin_code, created_at, updated_at, deleted_at)
    VALUES ('{EVENT_ID}', 'Android staging fixture', eur_id, '{pin}', now(), now(), NULL)
    ON CONFLICT (id) DO NOTHING;
  IF NOT EXISTS (SELECT 1 FROM event WHERE id = '{EVENT_ID}' AND pin_code = '{pin}'
      AND deleted_at IS NULL AND currency_id IN (SELECT id FROM currency
        WHERE code IN ('EUR', 'USD', 'RUB', 'JPY', 'TRY', 'AED'))) THEN
    RAISE EXCEPTION 'fixed fixture conflicts with existing data';
  END IF;
  IF EXISTS (SELECT 1 FROM expense WHERE event_id = '{EVENT_ID}') THEN
    RAISE EXCEPTION 'fixed fixture must remain empty; existing expenses were preserved';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM user_info WHERE event_id = '{EVENT_ID}' AND name = '{PERSON_NAME}') THEN
    INSERT INTO user_info (id, name, event_id, created_at, updated_at)
      VALUES ('{PERSON_ID}', '{PERSON_NAME}', '{EVENT_ID}', now(), now())
      ON CONFLICT (id) DO NOTHING;
  END IF;
  IF (SELECT count(*) FROM user_info WHERE event_id = '{EVENT_ID}' AND name = '{PERSON_NAME}') <> 1 THEN
    RAISE EXCEPTION 'fixed fixture has ambiguous participant names';
  END IF;
END; $fixture$;
COMMIT;
'''


def checked(command, **kwargs):
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                            check=False, timeout=90, **kwargs)
    if result.returncode:
        raise FixtureError('SSH/Compose/PostgreSQL fixture provisioning failed; inspect staging locally without logging credentials')
    return result.stdout


def install(pin):
    validate_pin(pin)
    compose = ['docker', 'compose', '--env-file', '.env', '-f', 'docker-compose-prod.yml']
    config = json.loads(checked(compose + ['config', '--format', 'json'], cwd=APP))
    services = config['services']
    if (services['nginx']['environment'].get('COMMONEX_API_HOST') != 'staging.commonex.ru'
            or services['db']['environment'].get('POSTGRES_DB') != 'commonex_staging'):
        raise FixtureError('fixture provisioning is restricted to the dedicated staging stack')
    schema = services['nest-backend-green']['environment']['POSTGRES_SCHEMA']
    sql = fixture_sql(pin, schema)
    checked(compose + ['exec', '-T', 'db', 'sh', '-c',
                      'exec psql --no-psqlrc --quiet --set ON_ERROR_STOP=1 '
                      '--username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'],
            cwd=APP, input=sql.encode('utf-8'))


def provision(pin, key):
    source = base64.b64encode(Path(__file__).read_bytes()).decode('ascii')
    bootstrap = "import base64;exec(compile(base64.b64decode(%r),'staging-fixture','exec'))" % source
    checked(['ssh', '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes',
             '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=20', '-i', str(key), TARGET,
             'sudo -n /usr/bin/python3 -c ' + shlex.quote(bootstrap) + ' --install'],
            input=json.dumps({'pin': pin}).encode('utf-8'))


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise FixtureError('staging API redirect refused')


def request_json(path, pin=None):
    context = ssl.create_default_context()
    context.minimum_version = ssl.TLSVersion.TLSv1_3
    opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=context))
    data = json.dumps({'pinCode': pin}).encode('utf-8') if pin else None
    request = urllib.request.Request(HOST + path, data=data,
                                     headers={'Content-Type': 'application/json'})
    try:
        with opener.open(request, timeout=30) as response:
            # Retained main images use Nest's default 201 for these read POSTs.
            if response.status not in ((200, 201) if data else (200,)):
                raise FixtureError(f'staging API read failed (HTTP {response.status})')
            body = response.read(1024 * 1024 + 1)
    except urllib.error.HTTPError as error:
        raise FixtureError(f'staging API read failed (HTTP {error.code}); verify fixture and current exchange rates') from None
    except urllib.error.URLError:
        raise FixtureError('staging HTTPS connection failed; verify DNS, network access, and TLS trust') from None
    if len(body) > 1024 * 1024:
        raise FixtureError('staging API response exceeded the bound')
    return json.loads(body)


def verify(pin, fetch=request_json):
    event = fetch(f'/api/v2/user/event/{EVENT_ID}', pin)
    if (event.get('id') != EVENT_ID or event.get('deletedAt') is not None
            or sum(user.get('name') == PERSON_NAME for user in event.get('users', [])) != 1):
        raise FixtureError('staging fixture is missing the required unique participant')
    if fetch(f'/api/v2/user/event/{EVENT_ID}/expenses', pin) != []:
        raise FixtureError('staging fixture is not empty; existing expenses were preserved')
    payload = fetch('/api/v3/user/currencies/all')
    currencies = payload.get('currencies', [])
    rates = payload.get('exchangeRate', {})
    if (not REQUIRED_CURRENCIES.issubset({row.get('code') for row in currencies})
            or any(not isinstance(rates.get(code), (float, int)) or isinstance(rates.get(code), bool)
                   or not math.isfinite(rates[code]) or rates[code] <= 0 for code in REQUIRED_CURRENCIES)
            or event.get('currencyId') not in {row.get('id') for row in currencies}):
        raise FixtureError('staging currencies and current UTC exchange rates are unavailable')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--provision', action='store_true', help='operator SSH bootstrap, then public verification')
    parser.add_argument('--staging-key', type=Path, help='operator staging SSH identity; never a CI admin key')
    parser.add_argument('--install', action='store_true', help=argparse.SUPPRESS)
    args = parser.parse_args()
    try:
        if args.install:
            install(json.loads(sys.stdin.buffer.read(4096))['pin'])
            return 0
        if args.provision and not args.staging_key:
            parser.error('--provision requires --staging-key')
        pin = fixture_pin()
        if args.provision:
            provision(pin, args.staging_key)
        verify(pin)
    except FixtureError as error:
        print(f'Staging fixture setup failed: {error}. Accumulated data was preserved.', file=sys.stderr)
        return 1
    except Exception:
        # HTTP and PostgreSQL errors can carry request credentials. Never print them.
        print('Staging fixture setup failed. Verify staging health, initialized currencies/rates, '
              'operator access, and the fixed fixture; accumulated data was not cleaned up.', file=sys.stderr)
        return 1
    print('Staging Android fixture and current currencies are ready.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
