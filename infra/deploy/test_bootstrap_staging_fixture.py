import contextlib
import io
import json
import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import uuid

from infra.deploy import bootstrap_staging_fixture as fixture


class FixtureTests(unittest.TestCase):
    def test_reads_the_existing_suite_credential_without_redefining_it(self):
        self.assertRegex(fixture.fixture_pin(), r'^[0-9]{4}$')
        with tempfile.TemporaryDirectory() as temp:
            suite = Path(temp) / 'test.kt'
            suite.write_text('fun testJoinExistingEvent() {}')
            with self.assertRaisesRegex(ValueError, 'could not be located'):
                fixture.fixture_pin(suite)

    def test_refuses_sql_inputs_outside_the_validated_fixture_shape(self):
        for pin, schema in [('not-a-pin', 'public'), ('1234', 'public; DROP SCHEMA public')]:
            with self.subTest(schema=schema), self.assertRaises(ValueError):
                fixture.fixture_sql(pin, schema)

    def test_public_verification_uses_existing_api_paths_and_requires_empty_expenses(self):
        event = {'id': fixture.EVENT_ID, 'deletedAt': None, 'currencyId': 'EUR',
                 'users': [{'name': fixture.PERSON_NAME}]}
        requests = []

        def fetch(path, pin=None):
            requests.append(path)
            if path.endswith('/expenses'):
                return []
            if path.endswith('/currencies/all'):
                return {'currencies': [{'id': code, 'code': code} for code in fixture.REQUIRED_CURRENCIES],
                        'exchangeRate': {code: 1.0 for code in fixture.REQUIRED_CURRENCIES}}
            return event

        fixture.verify('1234', fetch)
        self.assertEqual(requests, [f'/api/v2/user/event/{fixture.EVENT_ID}',
                                    f'/api/v2/user/event/{fixture.EVENT_ID}/expenses',
                                    '/api/v3/user/currencies/all'])
        with self.assertRaisesRegex(ValueError, 'not empty'):
            fixture.verify('1234', lambda path, pin=None: [{}] if path.endswith('/expenses') else event)
        with self.assertRaisesRegex(ValueError, 'exchange rates'):
            fixture.verify('1234', lambda path, pin=None: {} if path.endswith('/currencies/all') else fetch(path, pin))
        event['users'].append({'name': fixture.PERSON_NAME})
        with self.assertRaisesRegex(ValueError, 'unique participant'):
            fixture.verify('1234', fetch)

    def test_provisioning_keeps_credentials_off_ssh_arguments_and_pins_host_trust(self):
        with patch.object(fixture, 'checked') as checked:
            fixture.provision('1234', Path('identity'))
        command = checked.call_args.args[0]
        self.assertIn('StrictHostKeyChecking=yes', command)
        self.assertIn(fixture.TARGET, command)
        self.assertNotIn('1234', ' '.join(command))
        self.assertEqual(json.loads(checked.call_args.kwargs['input']), {'pin': '1234'})

    def test_install_refuses_a_production_stack_before_writing(self):
        config = {'services': {'nginx': {'environment': {'COMMONEX_API_HOST': 'dev-api.commonex.ru'}},
                               'db': {'environment': {'POSTGRES_DB': 'production'}}}}
        with patch.object(fixture, 'checked', return_value=json.dumps(config).encode()) as checked:
            with self.assertRaisesRegex(ValueError, 'dedicated staging'):
                fixture.install('1234')
        self.assertEqual(checked.call_count, 1)

    def test_public_reads_support_the_retained_main_images_success_status(self):
        for status in (200, 201):
            with self.subTest(status=status), patch.object(fixture.urllib.request, 'build_opener') as build:
                response = build.return_value.open.return_value.__enter__.return_value
                response.status = status
                response.read.return_value = b'{"id":"event"}'
                self.assertEqual(fixture.request_json('/api/v2/user/event/event', '1234'), {'id': 'event'})
        with patch.object(fixture.urllib.request, 'build_opener') as build:
            build.return_value.open.side_effect = fixture.urllib.error.HTTPError(
                'https://staging.commonex.ru', 403, 'secret pinCode=1234', {}, None)
            with self.assertRaisesRegex(fixture.FixtureError, 'HTTP 403') as caught:
                fixture.request_json('/api/v2/user/event/event', '1234')
            self.assertNotIn('1234', str(caught.exception))

    def test_diagnostics_never_print_the_credential_from_upstream_errors(self):
        with patch('sys.argv', ['fixture']), patch.object(fixture, 'fixture_pin', return_value='1234'), \
                patch.object(fixture, 'verify', side_effect=ValueError('secret pinCode=1234')), \
                contextlib.redirect_stderr(io.StringIO()) as output:
            self.assertEqual(fixture.main(), 1)
        self.assertNotIn('1234', output.getvalue())


@unittest.skipUnless(os.environ.get('COMMONEX_STAGING_FIXTURE_SSH_KEY'),
                     'SQL regressions require an operator staging SSH identity')
class FixturePostgresTests(unittest.TestCase):
    """Exercise real staging PostgreSQL in a uniquely named, isolated test schema."""

    def sql(self, source, success=True):
        shell = 'exec psql --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 ' \
                '--username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'
        command = ['ssh', '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes',
                   '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=20',
                   '-i', os.environ['COMMONEX_STAGING_FIXTURE_SSH_KEY'], fixture.TARGET,
                   'sudo -n docker compose --env-file /etc/commonex/app/.env '
                   '-f /etc/commonex/app/docker-compose-prod.yml exec -T db sh -c ' + shlex.quote(shell)]
        result = subprocess.run(command, input=source.encode(), stdout=subprocess.PIPE,
                                stderr=subprocess.DEVNULL, check=False, timeout=90)
        self.assertEqual(result.returncode == 0, success, 'isolated fixture SQL outcome differs')
        return result.stdout.decode().strip()

    def setUp(self):
        self.schema = 'fixture_test_' + uuid.uuid4().hex
        self.addCleanup(self.sql, f'DROP SCHEMA IF EXISTS {self.schema} CASCADE;')
        self.sql(f'''CREATE SCHEMA {self.schema};
CREATE TABLE {self.schema}.currency (LIKE public.currency INCLUDING ALL);
CREATE TABLE {self.schema}.event (LIKE public.event INCLUDING ALL);
CREATE TABLE {self.schema}.user_info (LIKE public.user_info INCLUDING ALL);
CREATE TABLE {self.schema}.expense (LIKE public.expense INCLUDING ALL);
INSERT INTO {self.schema}.currency SELECT * FROM public.currency WHERE code = 'EUR';
SET search_path TO {self.schema};
INSERT INTO event (id,name,currency_id,pin_code,created_at,updated_at)
  SELECT 'unrelated-event','Accumulated event',id,'1234',now(),now() FROM currency;
INSERT INTO expense (id,description,user_who_paid_id,currency_id,event_id,expense_type,split_information,
  is_custom_rate,created_at,updated_at)
  SELECT 'unrelated-expense','Accumulated expense','unrelated-person',id,'unrelated-event','expense',
  '[{{"userId":"unrelated-person","amount":42,"exchangedAmount":42}}]'::jsonb,false,now(),now() FROM currency;
''')

    def snapshot(self):
        return self.sql(f'''SET search_path TO {self.schema};
SELECT json_build_object('events', (SELECT json_agg(event ORDER BY id) FROM event),
 'people', (SELECT json_agg(user_info ORDER BY id) FROM user_info),
 'expenses', (SELECT json_agg(expense ORDER BY id) FROM expense));''')

    def test_repeated_bootstrap_preserves_fixture_and_unrelated_accumulated_rows(self):
        prior = json.loads(self.snapshot())
        self.sql(fixture.fixture_sql('1234', self.schema))
        first = self.snapshot()
        self.sql(fixture.fixture_sql('1234', self.schema))
        self.assertEqual(self.snapshot(), first)
        after = json.loads(first)
        self.assertEqual(after['expenses'], prior['expenses'])
        self.assertEqual(next(row for row in after['events'] if row['id'] == 'unrelated-event'), prior['events'][0])
        self.assertEqual(after['people'][0]['name'], fixture.PERSON_NAME)

    def test_conflicting_credential_rolls_back_without_modifying_existing_rows(self):
        self.sql(fixture.fixture_sql('1234', self.schema))
        before = self.snapshot()
        self.sql(fixture.fixture_sql('4321', self.schema), success=False)
        self.assertEqual(self.snapshot(), before)

    def test_existing_fixture_expenses_are_preserved_and_bootstrap_fails(self):
        self.sql(fixture.fixture_sql('1234', self.schema))
        self.sql(f"UPDATE {self.schema}.expense SET event_id = '{fixture.EVENT_ID}' WHERE id = 'unrelated-expense';")
        before = self.snapshot()
        self.sql(fixture.fixture_sql('1234', self.schema), success=False)
        self.assertEqual(self.snapshot(), before)


if __name__ == '__main__':
    unittest.main()
