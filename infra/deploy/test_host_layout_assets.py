from pathlib import Path
import unittest


class HostLayoutAssetsTest(unittest.TestCase):
    def test_logrotate_policy_is_root_only_and_uses_canonical_log(self) -> None:
        policy = (
            Path(__file__).with_name("commonex-deploy.logrotate").read_text(
                encoding="utf-8"
            )
        )

        self.assertTrue(policy.startswith("/var/log/commonex/deploy.log {\n"))
        self.assertIn("    create 0600 root root\n", policy)
        self.assertIn("    su root root\n", policy)
        self.assertNotIn("copytruncate", policy)
        self.assertNotIn("/var/log/commonex-deploy.log", policy)


if __name__ == "__main__":
    unittest.main()
