"""Open the local working PostgreSQL in psql, without printing its password."""
import argparse
import sys
import subprocess
import project

parser = argparse.ArgumentParser()
parser.add_argument('--users', action='store_true', help='Show users and BCrypt salt, not full password hashes')
args = parser.parse_args()
try:
    settings = project.read_env()
    project.ensure_docker()
    result = project.compose(settings, 'up', '-d', '--wait', '--wait-timeout', '120', 'postgres')
    if result.returncode:
        raise RuntimeError('PostgreSQL did not start.')
    command = ['exec'] + (['-T'] if args.users else []) + ['postgres', 'psql', '-U', settings['DB_USER'], '-d', settings['DB_NAME']]
    if args.users:
        command += ['-c', 'SELECT id, username, email, role, substring(password_hash from 1 for 7) AS bcrypt_parameters, substring(password_hash from 8 for 22) AS salt FROM app_users ORDER BY id']
    # Keep psql attached to this terminal for interactive input.
    sys.exit(subprocess.call(['docker', 'compose', '--project-name', project.PROJECT,
                             '--env-file', str(project.ENV_FILE), '-f', str(project.SERVER / 'compose.yaml'),
                             *command], env={**project.os.environ, **settings}))
except (OSError, RuntimeError, ValueError) as error:
    print(f'ERROR: {error}', file=sys.stderr)
    sys.exit(1)
